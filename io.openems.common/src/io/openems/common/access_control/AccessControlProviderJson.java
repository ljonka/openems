package io.openems.common.access_control;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.openems.common.channel.AccessMode;
import io.openems.common.exceptions.OpenemsError;
import io.openems.common.types.ChannelAddress;
import io.openems.common.utils.FileUtils;
import io.openems.common.utils.JsonKeys;
import io.openems.common.utils.JsonUtils;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static io.openems.common.utils.JsonKeys.*;

@Designate(ocd = ConfigJson.class, factory = true)
@Component( //
        name = "common.AccessControlProvider.AccessControlProviderJson", //
        immediate = true, //
        configurationPolicy = ConfigurationPolicy.REQUIRE)
public class AccessControlProviderJson implements AccessControlProvider {

    protected final Logger log = LoggerFactory.getLogger(AccessControlProviderJson.class);

    private String path;

    private int priority;

    @Activate
    void activate(ComponentContext componentContext, BundleContext bundleContext, ConfigJson config) {
        this.path = config.path();
        this.priority = config.priority();
    }

    public void initializeAccessControl(AccessControlDataManager accessControlDataManager) {
        StringBuilder sb = FileUtils.checkAndGetFileContent(path);
        if (sb == null) {
            // exception occurred. File could not be read
            return;
        }

        try {
            JsonElement config = JsonUtils.parse(sb.toString());
            handleUsers(JsonUtils.getAsJsonObject(config, USERS.value()), accessControlDataManager);
            handleRoles(JsonUtils.getAsJsonObject(config, ROLES.value()), accessControlDataManager);

        } catch (OpenemsError.OpenemsNamedException e) {
            this.log.warn("Unable to parse JSON-file [" + path + "]: " + e.getMessage());
        }
    }

    @Override
    public int priority() {
        return this.priority;
    }

    private void handleRoles(JsonObject jsonRoles, AccessControlDataManager accessControlDataManager) throws OpenemsError.OpenemsNamedException {
        Map<Role, List<RoleId>> createdRoles = new HashMap<>();
        for (Map.Entry<String, JsonElement> jsonRole : jsonRoles.entrySet()) {
            Role newRole = new Role();
            newRole.setId(new RoleId(jsonRole.getKey()));
            newRole.setDescription(JsonUtils.getAsString(jsonRole.getValue(), DESCRIPTION.value()));
            JsonArray jsonParents = JsonUtils.getAsJsonArray(jsonRole.getValue(), PARENTS.value());
            List<RoleId> parentRoleIds = new ArrayList<>();
            for (JsonElement jsonParent : jsonParents) {
                parentRoleIds.add(new RoleId(jsonParent.getAsString()));
            }

            if (createdRoles.put(newRole, parentRoleIds) != null) {
                // this means there was already a role assigned with the same id
                // -> this must not happen and means a invalid configuration
                throw new ConfigurationException("AccessControlProviderJson has a inconsistent role configuration. " +
                        "Role with id (" + newRole.getRoleId() + ") is configured at least twice.");
            }

            JsonObject jsonObject = JsonUtils.getAsJsonObject(jsonRole.getValue(), EDGES.value());
            for (Map.Entry<String, JsonElement> jsonEdgeEntry : jsonObject.entrySet()) {
                String edgeId = jsonEdgeEntry.getKey();
                JsonObject jsonRpcs = JsonUtils.getAsJsonObject(jsonEdgeEntry.getValue(), JsonKeys.JSON_RPC.value());
                Map<String, ExecutePermission> methodPermissionMapping = new HashMap<>();
                for (Map.Entry<String, JsonElement> methodPermissionEntry : jsonRpcs.entrySet()) {
                    methodPermissionMapping.put(
                            methodPermissionEntry.getKey(),
                            ExecutePermission.valueOf(JsonUtils.getAsString(methodPermissionEntry.getValue(), PERMISSION.value())));
                }
                newRole.addJsonRpcPermission(edgeId, methodPermissionMapping);
                JsonObject jsonChannels = JsonUtils.getAsJsonObject(jsonEdgeEntry.getValue(), CHANNELS.value());
                Map<ChannelAddress, AccessMode> channelPermissionMapping = new HashMap<>();
                for (Map.Entry<String, JsonElement> channelPermissionEntry : jsonChannels.entrySet()) {
                    String[] key = channelPermissionEntry.getKey().split(ChannelAddress.DELIMITER);
                    channelPermissionMapping.put(
                            new ChannelAddress(key[0], key[1]),
                            AccessMode.valueOf(JsonUtils.getAsString(channelPermissionEntry.getValue(), PERMISSION.value())));
                }
                newRole.addChannelPermissions(edgeId, channelPermissionMapping);
            }
        }

        resolveAndSetParentInheritance(createdRoles, accessControlDataManager);
    }

    /**
     * This method sets all the inheritances and also checks for loops and throws a exception in case
     *
     * @param createdRoles
     * @param accessControlDataManager
     */
    private void resolveAndSetParentInheritance(Map<Role, List<RoleId>> createdRoles, AccessControlDataManager accessControlDataManager) throws ConfigurationException {
        detectLoop(createdRoles);

        // since no exception has been thrown we can simply set the roles of the parents via the already given roleIds
        createdRoles.forEach((key, value) ->
                value.forEach(roleId ->
                        key.setParents(accessControlDataManager.getRoles().stream().filter(r ->
                                r.getRoleId().equals(roleId)).collect(Collectors.toSet()))));
        accessControlDataManager.addRoles(createdRoles.keySet(), true);
    }

    /**
     * This method checks for loops within the whole roles using a depth search
     *
     * @param createdRoles
     * @throws ConfigurationException
     */
    private void detectLoop(Map<Role, List<RoleId>> createdRoles) throws ConfigurationException {
        createdRoles.entrySet().stream().forEach(roleListEntry -> {

        });
        for (Map.Entry<Role, List<RoleId>> roleListEntry : createdRoles.entrySet()) {
            // the current role is just myself -> helps understanding if looking at the other roles from a pov
            RoleId myself = roleListEntry.getKey().getRoleId();
            Set<RoleId> seenRoleIds = new HashSet<>();
            seenRoleIds.add(myself);
            Stack<RoleId> rolesToLookAt = new Stack<>();
            roleListEntry.getValue().forEach(rolesToLookAt::push);

            // iterate over all parents of myself
            while (!rolesToLookAt.empty()) {
                RoleId parentRoleId = rolesToLookAt.pop();
                // fetch the role of the parentId of myself
                Role other = createdRoles.keySet().stream().filter(e -> e.getRoleId().equals(parentRoleId)).findFirst()
                        .orElseThrow(() -> new ConfigurationException(
                                "AccessControlProviderJson: There is a roleId assignment ("
                                        + parentRoleId + ") which points to a non existing role"));

                if (!seenRoleIds.contains(other.getRoleId())) {
                    // everything fine. role not part of the seen ones yet
                    seenRoleIds.add(other.getRoleId());

                    // add the parents of the other
                    createdRoles.get(other).forEach(rolesToLookAt::push);
                } else {
                    // loop detected!
                    throw new ConfigurationException("AccessControlProviderJson: Loop detected. Check your configuration!" +
                            "rolesToCompare(" + seenRoleIds + ", currentRole (" + other + ")");
                }
            }
        }
    }

    private void handleUsers(JsonObject jsonUsers, AccessControlDataManager accessControlDataManager) throws OpenemsError.OpenemsNamedException {
        for (Map.Entry<String, JsonElement> userJson : jsonUsers.entrySet()) {
            String userId = userJson.getKey();
            String username = JsonUtils.getAsString(userJson.getValue(), NAME.value());
            String description = JsonUtils.getAsString(userJson.getValue(), DESCRIPTION.value());
            String passwordBase64 = JsonUtils.getAsString(userJson.getValue(), PASSWORD.value());
            String saltBase64 = JsonUtils.getAsString(userJson.getValue(), SALT.value());
            String email = JsonUtils.getAsString(userJson.getValue(), EMAIL.value());
            RoleId roleId = new RoleId(JsonUtils.getAsString(userJson.getValue(), ROLE.value()));
            User newUser = new User(userId, username, description, email, passwordBase64, saltBase64, roleId);
            accessControlDataManager.addUser(newUser);
        }
    }
}
