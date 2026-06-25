import axios from 'axios'
import type { WorkbenchOverview } from '../types/workbench'

export async function fetchWorkbenchOverview() {
  const response = await axios.get<WorkbenchOverview>('/api/workbench/overview')
  return response.data
}
