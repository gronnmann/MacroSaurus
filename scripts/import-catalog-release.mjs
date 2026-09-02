#!/usr/bin/env node

import { readFile } from 'node:fs/promises'

const inputPath = process.argv[2]
if (!inputPath) {
    console.error('Usage: node scripts/import-catalog-release.mjs <normalized-release.json>')
    process.exit(2)
}

const apiUrl = (process.env.MACROSAURUS_API_URL || 'http://localhost:8080/api/v1').replace(/\/$/, '')
const token = process.env.MACROSAURUS_TOKEN
const userId = process.env.MACROSAURUS_USER_ID || 'dev-user'
const payload = JSON.parse(await readFile(inputPath, 'utf8'))
const headers = { 'Content-Type': 'application/json' }
if (token) headers.Authorization = `Bearer ${token}`
else headers['X-User-Id'] = userId

const response = await fetch(`${apiUrl}/admin/catalog-imports`, {
    method: 'POST',
    headers,
    body: JSON.stringify(payload),
})
const result = await response.json().catch(() => ({}))
if (!response.ok) {
    console.error(result.detail || `Import failed with HTTP ${response.status}`)
    process.exit(1)
}
console.log(JSON.stringify(result, null, 2))
