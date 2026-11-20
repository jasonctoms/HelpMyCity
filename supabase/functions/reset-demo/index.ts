// Puts the public demo back the way it started. It empties the issue-photos
// bucket, then runs supabase/seed.sql's reset_demo_data() again.
//
// The dataset itself does not live here. This function only handles the part SQL
// can't do, which is deleting the stored image bytes. Removing rows from
// storage.objects would leave the actual files orphaned in the backing store, so
// the Storage API has to do it.
//
// Scheduled daily by supabase/schedule.sql.

import { createClient } from 'jsr:@supabase/supabase-js@2'

const BUCKET = 'issue-photos'

// Storage lists one directory at a time, and photos are keyed <issueId>/<photoId>.
const PAGE = 100

/**
 * Timing-safe string compare. A plain `===` leaks the shared secret one character
 * at a time to anyone patient enough to time the response.
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

  // The top level is one folder per issue, and the files are one level down.
  for (let offset = 0; ; offset += PAGE) {
    const { data: folders, error } = await storage.list('', { limit: PAGE, offset })
    if (error) throw error
    if (!folders || folders.length === 0) break

    for (const folder of folders) {
      // A folder has no id, but a stray file at the root does.
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

  // Anyone who knows the URL can reach this function, and verify_jwt does not
  // protect anything here, since the publishable key is public and any visitor
  // has a valid JWT. The shared secret is what actually guards it.
  const given = request.headers.get('x-reset-secret') ?? ''
  if (!secretMatches(given, expected)) {
    return Response.json({ error: 'Forbidden.' }, { status: 403 })
  }

  // The secret key skips row-level security, and that is the point, because this
  // has to delete rows no normal caller can touch. It never leaves the server.
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
