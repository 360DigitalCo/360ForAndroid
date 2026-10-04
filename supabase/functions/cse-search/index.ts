// Deploy:  supabase functions deploy cse-search
// Secret:  supabase secrets set GOOGLE_CSE_API_KEY=<the same Google API key your image-search function uses>
//
// Why this exists: the website renders Google's Programmable Search *widget* (JavaScript) and
// scrapes it. A native Android UI can't run that widget, so this function asks the same engine
// (cx below) through Google's JSON API and returns it in the shape the app already merges with
// the 360 index results. Keeping the key here keeps it out of the APK.
// Note: the free JSON API allows ~100 queries/day; raise it in Google Cloud if you need more.

const CX = "e003eb0834b6b4be8";
const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...CORS, "Content-Type": "application/json" } });

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  const url = new URL(req.url);
  const q = (url.searchParams.get("q") || "").trim();
  const start = Math.min(91, Math.max(1, parseInt(url.searchParams.get("start") || "1", 10) || 1));
  if (!q) return json({ web: [] });

  const key = Deno.env.get("GOOGLE_CSE_API_KEY");
  if (!key) return json({ error: "GOOGLE_CSE_API_KEY is not set" }, 500);

  const api = new URL("https://www.googleapis.com/customsearch/v1");
  api.searchParams.set("key", key);
  api.searchParams.set("cx", CX);
  api.searchParams.set("q", q);
  api.searchParams.set("start", String(start));
  api.searchParams.set("num", "10");
  api.searchParams.set("safe", url.searchParams.get("safe") === "off" ? "off" : "active");

  const r = await fetch(api);
  if (!r.ok) return json({ error: `Search provider error (${r.status})` }, r.status === 429 ? 429 : 502);
  const d = await r.json();
  const web = (d.items || []).map((i: any) => ({
    url: i.link,
    title: i.title,
    desc: i.snippet,
    displayUrl: i.displayLink,
    thumb: i.pagemap?.cse_thumbnail?.[0]?.src,
  }));
  return json({ web, totalResults: Number(d.searchInformation?.totalResults || 0) });
});
