// Puts the public demo back to its starting state: empties the issue-photos
// bucket, then replays supabase/seed.sql's reset_demo_data().
//
// The dataset itself is not here. This function owns only the part SQL cannot
// do -- deleting the stored image bytes. Removing rows from storage.objects
// would leave the files themselves orphaned in the backing store, so the
// Storage API has to do it.
//
// Scheduled daily by supabase/schedule.sql.

import { createClient } from 'jsr:@supabase/supabase-js@2'

const BUCKET = 'issue-photos'

// Storage lists one directory at a time, and photos are keyed <issueId>/<photoId>.
const PAGE = 100

/**
 * Timing-safe string compare. A plain `===` leaks the shared secret one
 * character at a time to anyone willing to measure the response.
 */
function secretMatches(given: string, expected: string): boolean {
  const a = new TextEncoder().encode(given)
  const b = new TextEncoder().encode(expected)
  if (a.length !== b.length) return false
  let diff = 0
  for (let i = 0; i < a.length; i++) diff |= a[i] ^ b[i]
  return diff === 0
}

async function emptyPhotoBucket(supabase: ReturnType<typeof createClient>): Promise<number> {
  const storage = supabase.storage.from(BUCKET)
  let removed = 0

  // Top level is one folder per issue; the files sit one level down.
  for (let offset = 0; ; offset += PAGE) {
    const { data: folders, error } = await storage.list('', { limit: PAGE, offset })
    if (error) throw error
    if (!folders || folders.length === 0) break

    for (const folder of folders) {
      // A folder has no id; a stray file at the root does.
      if (folder.id !== null) {
        const { error: rmError } = await storage.remove([folder.name])
        if (rmError) throw rmError
        removed++
        continue
      }

      const { data: files, error: listError } = await storage.list(folder.name, { limit: 1000 })
      if (listError) throw listError
      if (!files || files.length === 0) continue

      const paths = files.map((file) => `${folder.name}/${file.name}`)
      const { error: rmError } = await storage.remove(paths)
      if (rmError) throw rmError
      removed += paths.length
    }

    if (folders.length < PAGE) break
  }

  return removed
}

Deno.serve(async (request) => {
  const expected = Deno.env.get('DEMO_RESET_SECRET')
  if (!expected) {
    return Response.json({ error: 'DEMO_RESET_SECRET is not set.' }, { status: 500 })
  }

  // The function is reachable by anyone who knows the URL, and verify_jwt is no
  // protection here: the publishable key is public, so any visitor holds a
  // valid JWT. The shared secret is what actually gates this.
  const given = request.headers.get('x-reset-secret') ?? ''
  if (!secretMatches(given, expected)) {
    return Response.json({ error: 'Forbidden.' }, { status: 403 })
  }

  // The secret key bypasses row-level security, which is the point: this has to
  // delete rows no ordinary caller may touch. It never leaves the server.
  const supabase = createClient(
    Deno.env.get('SUPABASE_URL')!,
    Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!,
    { auth: { persistSession: false } },
  )

  try {
    const photosRemoved = await emptyPhotoBucket(supabase)
    const { error } = await supabase.rpc('reset_demo_data')
    if (error) throw error

    return Response.json({ ok: true, photosRemoved, resetAt: new Date().toISOString() })
  } catch (cause) {
    console.error('demo reset failed', cause)
    return Response.json(
      { error: cause instanceof Error ? cause.message : String(cause) },
      { status: 500 },
    )
  }
})
