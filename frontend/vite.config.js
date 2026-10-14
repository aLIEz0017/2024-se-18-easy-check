/**
 * Vite 配置
 *
 * 组员A 负责维护此文件。
 *
 * 配置说明：
 * - resolve.alias：配置 @ 指向 src 目录，避免在 import 中使用冗长的相对路径
 *   （如 import request from '@/api/request' 而非 '../../api/request'）
 * - server.proxy：开发环境下将 /api 请求转发到后端 SpringBoot 服务（默认 8080 端口），
 *   解决跨域问题。生产环境部署时由 Nginx 统一处理。
 */

import { createLogger, defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'

const logger = createLogger()
const originalWarn = logger.warn

logger.warn = (message, options) => {
  if (message.includes('INVALID_ANNOTATION') && message.includes('node_modules/@vueuse/core')) {
    return
  }

  originalWarn(message, options)
}

// https://vite.dev/config/
export default defineConfig({
  customLogger: logger,
  plugins: [vue(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',  // 后端 SpringBoot 服务地址
        changeOrigin: true
      }
    }
  },
  build: {
    chunkSizeWarningLimit: 1100,
    rolldownOptions: {
      output: {
        codeSplitting: true,
        manualChunks(id) {
          if (!id.includes('node_modules')) return

          if (id.includes('echarts') || id.includes('zrender')) {
            return 'vendor-charts'
          }

          if (id.includes('element-plus') || id.includes('@element-plus')) {
            return 'vendor-element-plus'
          }

          if (id.includes('vue') || id.includes('pinia')) {
            return 'vendor-vue'
          }

          if (id.includes('gsap')) {
            return 'vendor-animation'
          }

          return 'vendor'
        }
      }
    }
  }
})
