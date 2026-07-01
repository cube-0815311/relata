import axios, { AxiosError } from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({
  timeout: 12000
})

let lastErrorAt = 0
let lastErrorMessage = ''

function notifyApiError(message: string) {
  const now = Date.now()
  if (message === lastErrorMessage && now - lastErrorAt < 1800) {
    return
  }
  lastErrorMessage = message
  lastErrorAt = now
  ElMessage.error(message)
}

function apiErrorMessage(error: unknown) {
  if (!axios.isAxiosError(error)) {
    return '接口调用失败，请稍后重试'
  }

  const axiosError = error as AxiosError<{ message?: string; error?: string }>
  if (axiosError.response) {
    const serverMessage = axiosError.response.data?.message || axiosError.response.data?.error
    return serverMessage || `接口请求失败（${axiosError.response.status}）`
  }

  if (axiosError.code === 'ECONNABORTED') {
    return '接口请求超时，请检查后端服务或网络连接'
  }

  return '接口无法连接，请确认后端服务已启动'
}

http.interceptors.response.use(
  (response) => response,
  (error) => {
    notifyApiError(apiErrorMessage(error))
    return Promise.reject(error)
  }
)

export default http
