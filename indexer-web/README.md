# Inqwise Indexer Console

Internal read-only operations console for the local Inqwise Indexer node.

The React/Vite workspace is located at `src/main/frontend`; the Java delivery
wrapper uses the standard Maven `src/main/java` layout.

The module follows the Vert.x React SPA pattern:

- Vite serves the React application with hot reload during frontend development.
- Maven builds the static SPA and copies it to the classpath `webroot`.
- `IndexerWebVerticle` serves that webroot in packaged/runtime use.
- Both development and runtime expose the same `/api/{service}` browser paths.
- The Vert.x wrapper forwards those paths to the existing internal REST ports.

The wrapper is a delivery adapter. It does not depend on Indexer domain/runtime
classes, EventBus services, or Gateway.

## Hot-reload development

Start the local Indexer node, then:

```sh
cd src/main/frontend
npm install
npm run dev
```

Open `http://localhost:3001`. Vite forwards API requests directly to the local
Indexer services while the packaged console remains available from the node on
port `3000`.

## Vert.x-wrapped runtime

Build the frontend and Java wrapper as dependencies of the node application:

```sh
mvn -pl indexer-node-application -am package
```

Build and start the single node container:

```sh
./run-local.sh
```

`IndexerNodeApplicationVerticle` starts the Indexer node and deploys
`IndexerWebVerticle` in the same Vert.x JVM. The `web` section in
`deployment/local/indexer-node.json` exposes the wrapper on `0.0.0.0:3000` and
uses loopback addresses for the node-owned REST services.

## Verification

```sh
cd src/main/frontend
npm run build
npm test
npm run lint
cd ../../../..
mvn -pl indexer-web -am test
```

This interface is not approved for public exposure. Production identity and
authorization remain deployment-owned decisions.
