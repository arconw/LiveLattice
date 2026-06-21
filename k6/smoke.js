import http from "k6/http";
import { check } from "k6";

export const options = {
  vus: 1,
  duration: "5s"
};

export default function () {
  const response = http.get("http://localhost:3000/health");
  check(response, {
    "gateway health is ok": r => r.status === 200
  });
}

