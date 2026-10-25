import { useEffect } from "react";

const APP_NAME = "IOTIQ Partner ERP";

/** Sets the browser tab title ("Users · IOTIQ Partner ERP"). */
export function useDocumentTitle(title?: string) {
  useEffect(() => {
    document.title = title ? `${title} · ${APP_NAME}` : APP_NAME;
  }, [title]);
}
