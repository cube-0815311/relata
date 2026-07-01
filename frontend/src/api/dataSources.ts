import type {
  DataSourceForm,
  DataSourceItem,
  MetadataCollectResponse,
  MetadataTablePage,
  TestConnectionResponse
} from '../types/dataSource'
import http from './http'

export async function fetchDataSources() {
  const response = await http.get<DataSourceItem[]>('/api/data-sources')
  return response.data
}

export async function createDataSource(payload: DataSourceForm) {
  const response = await http.post<DataSourceItem>('/api/data-sources', payload)
  return response.data
}

export async function updateDataSource(id: number, payload: DataSourceForm) {
  const response = await http.put<DataSourceItem>(`/api/data-sources/${id}`, payload)
  return response.data
}

export async function deleteDataSource(id: number) {
  await http.delete(`/api/data-sources/${id}`)
}

export async function testDataSourceConnection(id: number) {
  const response = await http.post<TestConnectionResponse>(`/api/data-sources/${id}/test`)
  return response.data
}

export async function collectDataSourceMetadata(id: number) {
  const response = await http.post<MetadataCollectResponse>(`/api/data-sources/${id}/metadata`)
  return response.data
}

export async function fetchMetadataTables(id: number, params: { keyword?: string; page: number; size: number }) {
  const response = await http.get<MetadataTablePage>(`/api/data-sources/${id}/metadata/tables`, { params })
  return response.data
}

export async function deleteMetadataTable(dataSourceId: number, tableId: string) {
  await http.delete(`/api/data-sources/${dataSourceId}/metadata/tables/${tableId}`)
}
