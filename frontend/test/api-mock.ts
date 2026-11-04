/**
 * A fake backend for component tests. `lib/api` captures `fetch` when it is imported, so the setup file installs
 * {@link fakeFetch} first and each test decides the answers with {@link mockApi}.
 */
type Handler = (request: Request, body: unknown) => Response | Promise<Response>;

const unmocked: Handler = (request) =>
  new Response(JSON.stringify({ detail: `No mock for ${request.method} ${new URL(request.url).pathname}` }), {
    status: 599,
    headers: { "Content-Type": "application/problem+json" },
  });

let handler: Handler = unmocked;

/** Every request the code under test sent, in order. */
export const requests: { method: string; path: string; headers: Headers; body: unknown }[] = [];

export function mockApi(h: Handler) {
  handler = h;
}

export function resetApi() {
  handler = unmocked;
  requests.length = 0;
}

/** A JSON response, e.g. `json({ id: "1" })` or `json({ detail: "Nope" }, 409)`. */
export function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });
}

/** Routes by "METHOD /path" (query string ignored); anything else is a 599 so a missing mock is obvious. */
export function routes(table: Record<string, (body: unknown, request: Request) => Response>) {
  mockApi((request, body) => {
    const key = `${request.method} ${new URL(request.url).pathname}`;
    return table[key]?.(body, request) ?? unmocked(request, body);
  });
}

export const fakeFetch: typeof fetch = async (input, init) => {
  const request = input instanceof Request ? input : new Request(new URL(String(input), "http://localhost:3000"), init);
  // A file upload's body is not read: a jsdom File inside Node's FormData never finishes streaming.
  const upload = request.headers.get("Content-Type")?.startsWith("multipart/form-data");
  const text = request.method === "GET" || upload ? "" : await request.clone().text();
  const body = text ? parse(text) : undefined;
  requests.push({ method: request.method, path: new URL(request.url).pathname + new URL(request.url).search, headers: request.headers, body });
  return handler(request, body);
};

/** JSON bodies are parsed; anything else (a file upload) is kept as text. */
function parse(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}
