package io.openems.backend.b2bwebsocket;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.openems.backend.metadata.api.BackendUser;
import io.openems.common.exceptions.OpenemsError;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.websocket.SubscribedChannelsWorker;

public class WsData extends io.openems.common.websocket.WsData {

	private final SubscribedChannelsWorker worker;
	private CompletableFuture<BackendUser> user = new CompletableFuture<BackendUser>();

	public WsData(B2bWebsocket parent) {
		this.worker = new SubscribedChannelsWorkerMultipleEdges(parent, this);
	}

	@Override
	public void dispose() {
		this.worker.dispose();
	}

	public void setUser(BackendUser user) {
		this.user.complete(user);
	}

	// TODO Use "Future<User>" in all bundles to avoid authenticated failed errors
	// if the websocket had not been fully opened before the first JSON-RPC Request
	public CompletableFuture<BackendUser> getUser() {
		return this.user;
	}

	public BackendUser getUserWithTimeout(long timeout, TimeUnit unit) throws OpenemsNamedException {
		try {
			return this.user.get(timeout, unit);
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			throw OpenemsError.COMMON_USER_NOT_AUTHENTICATED.exception("UNKNOWN");
		}
	}

	public Optional<BackendUser> getUserOpt() {
		return Optional.ofNullable(this.user.getNow(null));
	}

	/**
	 * Gets the SubscribedChannelsWorker to take care of subscribe to CurrentData.
	 * 
	 * @return the SubscribedChannelsWorker
	 */
	public SubscribedChannelsWorker getSubscribedChannelsWorker() {
		return this.worker;
	}

	@Override
	public String toString() {
		if (this.user == null) {
			return "B2bWebsocket.WsData [user=UNKNOWN]";
		} else {
			return "B2bWebsocket.WsData [user=" + user + "]";
		}
	}
}
