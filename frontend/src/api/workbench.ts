import type {
  AiModelsResponse,
  AnalyzeProgressEvent,
  AnalyzeRelationsRequest,
  AnalyzeRelationsResponse,
  AssociatedDataRequest,
  AssociatedDataResponse,
  CandidateRelation,
  RelationModelDetail,
  RelationModelPage,
  SaveRelationModelRequest,
  WorkbenchOverview
} from '../types/workbench'
import http from './http'

export async function fetchWorkbenchOverview() {
  const response = await http.get<WorkbenchOverview>('/api/workbench/overview')
  return response.data
}

export async function fetchAiModels() {
  const response = await http.get<AiModelsResponse>('/api/ai/models')
  return response.data
}

export async function analyzeRelations(payload: AnalyzeRelationsRequest) {
  const response = await http.post<AnalyzeRelationsResponse>('/api/ai/relations/analyze', payload)
  return response.data
}

export async function fetchRelationModels(params: { keyword?: string; page: number; size: number }) {
  const response = await http.get<RelationModelPage>('/api/workbench/relation-models', { params })
  const data = response.data as RelationModelPage & {
    items?: RelationModelPage['models']
    list?: RelationModelPage['models']
    records?: RelationModelPage['models']
  }
  return {
    page: data.page,
    size: data.size,
    total: data.total,
    models: data.models || data.items || data.list || data.records || []
  }
}

export async function fetchRelationModelDetail(id: string) {
  const response = await http.get<RelationModelDetail>(`/api/workbench/relation-models/${id}`)
  return response.data
}

export async function saveRelationModel(payload: SaveRelationModelRequest) {
  const response = await http.post<RelationModelDetail>('/api/workbench/relation-models', payload)
  return response.data
}

export async function queryAssociatedData(payload: AssociatedDataRequest) {
  const response = await http.post<AssociatedDataResponse>('/api/query/associated-data', payload)
  return response.data
}

export async function analyzeRelationsStream(
  payload: AnalyzeRelationsRequest,
  handlers: {
    onProgress?: (event: AnalyzeProgressEvent) => void
    onRelation?: (event: CandidateRelation) => void
    onResult?: (event: AnalyzeRelationsResponse) => void
    onError?: (message: string) => void
  },
  signal?: AbortSignal
) {
  const response = await fetch('/api/ai/relations/analyze/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream'
    },
    body: JSON.stringify(payload),
    signal
  })

  if (!response.ok || !response.body) {
    const message = await response.text()
    throw new Error(message || `接口请求失败（${response.status}）`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { value, done } = await reader.read()
    if (done) {
      break
    }
    buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
    const chunks = buffer.split('\n\n')
    buffer = chunks.pop() || ''
    chunks.forEach((chunk) => handleSseChunk(chunk, handlers))
  }
  if (buffer.trim()) {
    handleSseChunk(buffer, handlers)
  }
}

function handleSseChunk(
  chunk: string,
  handlers: {
    onProgress?: (event: AnalyzeProgressEvent) => void
    onRelation?: (event: CandidateRelation) => void
    onResult?: (event: AnalyzeRelationsResponse) => void
    onError?: (message: string) => void
  }
) {
  const lines = chunk.split('\n')
  const event = lines.find((line) => line.startsWith('event:'))?.slice(6).trim() || 'message'
  const data = lines
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart())
    .join('\n')

  if (!data) {
    return
  }
  if (event === 'progress') {
    handlers.onProgress?.(JSON.parse(data) as AnalyzeProgressEvent)
    return
  }
  if (event === 'relation') {
    handlers.onRelation?.(JSON.parse(data) as CandidateRelation)
    return
  }
  if (event === 'result') {
    handlers.onResult?.(JSON.parse(data) as AnalyzeRelationsResponse)
    return
  }
  if (event === 'error') {
    handlers.onError?.(data)
  }
}
