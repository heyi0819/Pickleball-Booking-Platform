import type { PropsWithChildren } from "react";

export function PageShell({ children }: PropsWithChildren) {
  return <main>{children}</main>;
}
