export interface ColumnItem {
  name: string
  type: string
  badge?: string
  comment: string
}

export interface TableNode {
  tableName: string
  comment: string
  status: 'confirmed' | 'recommended' | 'candidate'
  x: number
  y: number
  columns: ColumnItem[]
}

export interface RelationEdge {
  source: string
  target: string
  sourceColumn?: string
  targetColumn?: string
  label: string
  relationType: string
  confidence: number
  sourceType: string
  reason?: string
  confirmed: boolean
}

export interface RelationInspector {
  sourceTable: string
  sourceColumn: string
  targetTable: string
  targetColumn: string
  relationType: string
  confidence: number
  reason: string
}

export interface WorkbenchOverview {
  selectedDataSource: string
  selectedModel: string
  tables: string[]
  nodes: TableNode[]
  edges: RelationEdge[]
  inspector: RelationInspector
}

export interface AiModelOption {
  id: string
  name: string
  provider: string
  enabled: boolean
  description: string
}

export interface AiModelsResponse {
  defaultModel: string
  models: AiModelOption[]
}

export interface AnalyzeRelationsRequest {
  dataSourceId: string
  mainTable: string
  model: string
}

export interface CandidateRelation {
  sourceTable: string
  sourceColumn: string
  targetTable: string
  targetColumn: string
  relationType: string
  confidence: number
  reason: string
}

export interface AnalyzeRelationsResponse {
  model: string
  relations: CandidateRelation[]
}

export interface AnalyzeProgressEvent {
  message: string
  timestamp: number
}

export interface RelationModelListItem {
  id: string
  name: string
  dataSourceId: number
  dataSourceName: string
  description: string
  tableNames: string[]
  nodeCount: number
  edgeCount: number
}

export interface RelationModelPage {
  page: number
  size: number
  total: number
  models: RelationModelListItem[]
}

export interface RelationModelDetail {
  id: string
  name: string
  dataSourceId: number
  dataSourceName: string
  description: string
  nodes: TableNode[]
  edges: RelationEdge[]
}

export interface SaveRelationModelRequest {
  id?: string
  name: string
  dataSourceId: number
  description: string
  nodes: TableNode[]
  edges: RelationEdge[]
}

export interface AssociatedDataRequest {
  relationModelId: string
  mainTable: string
  keyColumn: string
  keyValue: string
}

export interface QueryTableResult {
  tableName: string
  comment: string
  relationPath: string
  columns: string[]
  rowCount: number
  rows: Record<string, unknown>[]
}

export interface AssociatedDataResponse {
  tables: QueryTableResult[]
}
