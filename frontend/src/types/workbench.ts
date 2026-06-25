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
  label: string
  relationType: string
  confidence: number
  sourceType: string
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
