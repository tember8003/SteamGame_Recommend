import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vitest/config'

// tsconfig 의 "@/*" -> "./*" 경로 별칭을 vitest 실행 시에도 그대로 풀어줌
// (타입 전용 import 는 컴파일 단계에서 지워져 별칭 없이도 통과하지만,
// 실제 런타임 import 는 이 alias 가 없으면 해석되지 않음)
const rootDir = fileURLToPath(new URL('.', import.meta.url))

export default defineConfig({
  test: {
    environment: 'jsdom',
    setupFiles: ['./vitest.setup.ts'],
    include: ['lib/**/*.test.ts', 'components/**/*.test.ts', 'components/**/*.test.tsx', 'app/**/*.test.ts', 'app/**/*.test.tsx'],
  },
  resolve: {
    alias: {
      '@': rootDir,
    },
  },
})
