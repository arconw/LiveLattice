import { start } from "./main";

start().catch((error) => {
  console.error(error);
  process.exit(1);
});