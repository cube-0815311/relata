<template>
  <div class="app-shell">
    <header class="topbar">
      <div class="brand">
        <div class="brand-mark">R</div>
        <div>
          <h1>Relata</h1>
          <p>数据关系依赖智能识别</p>
        </div>
      </div>
      <div class="topbar-controls">
        <el-select v-model="selectedDataSource" size="small" class="select">
          <el-option label="mysql-dev" value="mysql-dev" />
          <el-option label="h2-local" value="h2-local" />
        </el-select>
        <el-select v-model="selectedModel" size="small" class="select">
          <el-option label="订单关系模型" value="订单关系模型" />
        </el-select>
        <el-button size="small" :icon="Refresh">重新采集</el-button>
        <el-button type="primary" size="small" :icon="MagicStick">AI梳理关系</el-button>
        <el-button size="small" :icon="FolderChecked">保存模型</el-button>
      </div>
    </header>

    <main class="workspace">
      <aside class="left-panel">
        <section class="panel-section">
          <div class="section-title">
            <span>表资源</span>
            <el-tag size="small" type="info">{{ overview?.tables.length || 0 }}</el-tag>
          </div>
          <el-input v-model="keyword" size="small" placeholder="搜索表名 / 注释" :prefix-icon="Search" />
          <div class="table-list">
            <button
              v-for="table in filteredTables"
              :key="table"
              :class="['table-item', { active: table === 'ORDER_INFO' }]"
            >
              <Coin class="table-icon" />
              <span>{{ table }}</span>
            </button>
          </div>
        </section>

        <section class="panel-section ai-card">
          <div class="section-title">
            <span>AI 推荐</span>
            <el-tag size="small" type="warning">草稿</el-tag>
          </div>
          <p>已识别 4 条候选关系，其中 2 条置信度高于 90%。</p>
          <el-button size="small" type="primary" plain>接受高置信度关系</el-button>
        </section>
      </aside>

      <section class="canvas-area">
        <div class="canvas-toolbar">
          <el-button :icon="Rank" circle size="small" title="自动布局" />
          <el-button :icon="ZoomIn" circle size="small" title="放大" />
          <el-button :icon="ZoomOut" circle size="small" title="缩小" />
          <el-button :icon="FullScreen" circle size="small" title="适配画布" />
          <el-button :icon="Check" circle size="small" title="确认关系" />
        </div>

        <svg class="edge-layer" viewBox="0 0 1120 760" preserveAspectRatio="none">
          <defs>
            <marker id="arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
              <path d="M 0 0 L 8 4 L 0 8 z" fill="#7f93ad" />
            </marker>
          </defs>
          <g v-for="edge in positionedEdges" :key="edge.label">
            <path
              :d="edge.path"
              :class="['edge-path', { confirmed: edge.confirmed }]"
              marker-end="url(#arrow)"
            />
            <text :x="edge.labelX" :y="edge.labelY" class="edge-label">{{ edge.shortLabel }}</text>
          </g>
        </svg>

        <article
          v-for="node in overview?.nodes"
          :key="node.tableName"
          :class="['table-node', node.status]"
          :style="{ left: `${node.x}px`, top: `${node.y}px` }"
        >
          <div class="node-header">
            <div>
              <strong>{{ node.tableName }}</strong>
              <span>{{ node.comment }}</span>
            </div>
            <el-tag size="small" :type="node.status === 'confirmed' ? 'success' : node.status === 'recommended' ? 'warning' : 'info'">
              {{ statusText[node.status] }}
            </el-tag>
          </div>
          <div class="field-list">
            <div v-for="column in node.columns" :key="column.name" class="field-row">
              <span class="field-name">
                <span v-if="column.badge" class="field-badge">{{ column.badge }}</span>
                {{ column.name }}
              </span>
              <span class="field-type">{{ column.type }}</span>
            </div>
          </div>
        </article>
      </section>

      <aside class="right-panel">
        <section class="panel-section">
          <div class="section-title">
            <span>关系属性</span>
            <el-tag size="small" type="success">0.93</el-tag>
          </div>
          <div class="property-grid" v-if="overview">
            <label>源字段</label>
            <strong>{{ overview.inspector.sourceTable }}.{{ overview.inspector.sourceColumn }}</strong>
            <label>目标字段</label>
            <strong>{{ overview.inspector.targetTable }}.{{ overview.inspector.targetColumn }}</strong>
            <label>关系类型</label>
            <strong>{{ overview.inspector.relationType }}</strong>
            <label>参与查询</label>
            <el-switch :model-value="true" />
          </div>
          <div class="reason-box" v-if="overview">{{ overview.inspector.reason }}</div>
          <div class="button-row">
            <el-button size="small" type="primary">保存关系</el-button>
            <el-button size="small" type="danger" plain>删除关系</el-button>
          </div>
        </section>

        <section class="panel-section chat-card">
          <div class="section-title">
            <span>AI 问数</span>
            <el-tag size="small">SELECT</el-tag>
          </div>
          <div class="chat-context">
            <span>{{ selectedDataSource }}</span>
            <span>{{ selectedModel }}</span>
          </div>
          <el-input
            v-model="question"
            type="textarea"
            :rows="4"
            placeholder="例如：最近7天每天的订单数是多少？"
          />
          <el-button class="send-button" type="primary" :icon="Promotion">生成统计 SQL</el-button>
        </section>
      </aside>
    </main>

    <footer class="query-drawer">
      <div class="query-form">
        <strong>关联查询</strong>
        <el-select :model-value="'ORDER_INFO'" size="small" class="mini-select">
          <el-option label="ORDER_INFO" value="ORDER_INFO" />
        </el-select>
        <el-input :model-value="'ID'" size="small" class="mini-input" />
        <el-input :model-value="'10001'" size="small" class="value-input" />
        <el-button size="small" type="primary" :icon="DataAnalysis">执行查询</el-button>
      </div>
      <div class="result-tabs">
        <button class="active">查询计划</button>
        <button>关联数据</button>
        <button>同步 SQL</button>
      </div>
      <div class="result-summary">
        <span><b>ORDER_INFO</b> 1 行</span>
        <span><b>ORDER_ITEM</b> 3 行</span>
        <span><b>PAYMENT_RECORD</b> 1 行</span>
        <el-tag size="small" type="success">READY</el-tag>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  Check,
  DataAnalysis,
  Coin,
  FolderChecked,
  FullScreen,
  MagicStick,
  Promotion,
  Rank,
  Refresh,
  Search,
  ZoomIn,
  ZoomOut
} from '@element-plus/icons-vue'
import { fetchWorkbenchOverview } from '../api/workbench'
import type { WorkbenchOverview } from '../types/workbench'

const overview = ref<WorkbenchOverview>()
const keyword = ref('')
const question = ref('')
const selectedDataSource = ref('mysql-dev')
const selectedModel = ref('订单关系模型')

const statusText = {
  confirmed: '已确认',
  recommended: 'AI推荐',
  candidate: '候选'
}

const filteredTables = computed(() => {
  const tables = overview.value?.tables || []
  if (!keyword.value) {
    return tables
  }
  return tables.filter((table) => table.toLowerCase().includes(keyword.value.toLowerCase()))
})

const positionedEdges = computed(() => {
  if (!overview.value) {
    return []
  }
  const nodeMap = new Map(overview.value.nodes.map((node) => [node.tableName, node]))
  return overview.value.edges.map((edge) => {
    const source = nodeMap.get(edge.source)
    const target = nodeMap.get(edge.target)
    if (!source || !target) {
      return { ...edge, path: '', labelX: 0, labelY: 0, shortLabel: edge.label }
    }
    const sx = source.x + 110
    const sy = source.y + 118
    const tx = target.x + 110
    const ty = target.y + 20
    const midY = (sy + ty) / 2
    return {
      ...edge,
      path: `M ${sx} ${sy} C ${sx} ${midY}, ${tx} ${midY}, ${tx} ${ty}`,
      labelX: (sx + tx) / 2 - 58,
      labelY: midY - 8,
      shortLabel: edge.label.replace('ORDER_INFO.', '').replace('ORDER_ITEM.', '').replace('PAYMENT_RECORD.', '')
    }
  })
})

onMounted(async () => {
  overview.value = await fetchWorkbenchOverview()
  selectedDataSource.value = overview.value.selectedDataSource
  selectedModel.value = overview.value.selectedModel
})
</script>
