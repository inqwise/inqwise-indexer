import type { MouseEvent, ReactNode } from "react";

import { entityHref } from "../entity-navigation";
import type { EntityDestination } from "../entity-navigation";

export default function EntityLink({
  children,
  className,
  destination,
  onNavigate,
}: {
  children: ReactNode;
  className?: string;
  destination: EntityDestination;
  onNavigate: () => void;
}) {
  const href = entityHref(destination);

  function navigate(event: MouseEvent<HTMLAnchorElement>) {
    if (
      event.button !== 0 ||
      event.altKey ||
      event.ctrlKey ||
      event.metaKey ||
      event.shiftKey
    ) {
      return;
    }
    event.preventDefault();
    window.history.pushState(null, "", href);
    onNavigate();
    window.dispatchEvent(new HashChangeEvent("hashchange"));
  }

  return (
    <a className={className} href={href} onClick={navigate}>
      {children}
    </a>
  );
}
