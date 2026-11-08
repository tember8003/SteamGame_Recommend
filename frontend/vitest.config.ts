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
    // 테스트 실행 환경의 시간대를 UTC 로 고정한다, 이 기기의 Node 기본 시간대(Asia/Seoul)와 달라야
    // formatKst/formatKstShort 가 timeZone 옵션 없이도 우연히 맞는 값을 내는 상황을 막을 수 있다
    env: { TZ: 'UTC' },
  },
  resolve: {
    alias: {
      '@': rootDir,
    },
  },
})
