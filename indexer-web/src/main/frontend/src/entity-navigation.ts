export type EntityDestination =
  | { kind: "target"; id: number }
  | { kind: "indexer"; id: number };

export function entityHref(destination: EntityDestination): string {
  const url = new URL(window.location.href);
  if (destination.kind === "target") {
    url.searchParams.delete("indexer");
    url.searchParams.set("target", String(destination.id));
    url.hash = "targets";
  } else {
    url.searchParams.delete("target");
    url.searchParams.set("indexer", String(destination.id));
    url.hash = "indexers";
  }
  return `${url.pathname}${url.search}${url.hash}`;
}
