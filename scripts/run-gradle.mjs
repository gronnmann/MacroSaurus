import { spawnSync } from 'node:child_process'
import process from 'node:process'

const wrapper = process.platform === 'win32' ? 'gradlew.bat' : './gradlew'
const result = spawnSync(wrapper, process.argv.slice(2), {
    cwd: new URL('..', import.meta.url),
    stdio: 'inherit',
    shell: process.platform === 'win32',
})

if (result.error) {
    console.error(result.error.message)
    process.exit(1)
}

process.exit(result.status ?? 1)
