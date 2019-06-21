import { Environment } from "../app/shared/type/environment";

export const environment: Environment = {
  production: false,
  debugMode: true,

  // For OpenEMS Edge
  url: "ws://" + "localhost" + ":8082",
  backend: "OpenEMS Backend"

  // For OpenEMS Backend
  //url: "ws://" + location.hostname + ":8078",
  //backend: "OpenEMS Backend",

  //url: "ws://localhost:8082",
  //backend: "OpenEMS Backend",

};
