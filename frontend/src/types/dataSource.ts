export interface ColumnMetadata {
  name: string
  type: string
  primaryKey: boolean
  foreignKey: boolean
  comment: string
}

export interface TableMetadata {
  name: string
  comment: string
  columns: ColumnMetadata[]
}

export interface DataSourceItem {
  id: number
  name: string
  databaseType: string
  jdbcUrl: string
  username: string
  description: string
  status: 'CONNECTED' | 'FAILED' | 'NOT_TESTED'
  lastTestedAt?: string
  lastCollectedAt?: string
  tableCount: number
  metadata: TableMetadata[]
}

export interface DataSourceForm {
  name: string
  databaseType: string
  jdbcUrl: string
  username: string
  password: string
  description: string
}

export interface TestConnectionResponse {
  success: boolean
  message: string
  testedAt: string
}

export interface MetadataCollectResponse {
  dataSourceId: number
  dataSourceName: string
  tableCount: number
  columnCount: number
  collectedAt: string
  tables: TableMetadata[]
}

export interface MetadataTableItem extends TableMetadata {
  id: string
  columnCount: number
}

export interface MetadataTablePage {
  dataSourceId: number
  page: number
  size: number
  total: number
  tables: MetadataTableItem[]
}
