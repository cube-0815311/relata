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
        <el-button size="small" :icon="Connection" :disabled="activeSubMenu !== '关系图编辑' && activeSubMenu !== '新增模型'" @click="openRelationConfigDialog()">
          配置关系
        </el-button>
        <el-button size="small" :icon="FolderChecked" @click="saveRelationModelDraft">保存模型</el-button>
      </div>
    </header>

    <main class="workspace">
      <nav class="module-nav" aria-label="主菜单">
        <div v-for="group in menuGroups" :key="group.title" class="menu-group">
          <button :class="['menu-title', { active: activeModule === group.title }]" @click="selectModule(group.title)">
            <span>{{ group.title }}</span>
          </button>
          <button
            v-for="item in group.items"
            :key="item"
            :class="['menu-subitem', { active: activeSubMenu === item }]"
            @click="selectMenu(group.title, item)"
          >
            {{ item }}
          </button>
        </div>
      </nav>

      <section v-if="activeModule === '首页'" class="home-content">
        <div class="home-hero">
          <div>
            <el-tag type="primary" size="small">关系依赖智能建模</el-tag>
            <h2>Relata</h2>
            <p>
              面向数据库关系梳理、关联查询和 SQL 生成的工作台。你可以从数据源接入开始，
              采集元数据后创建关系模型，拖入表资源并配置实体之间的关联关系。
            </p>
          </div>
          <div class="home-actions">
            <el-button type="primary" :icon="Plus" @click="activeSubMenu = '数据源列表'">接入数据源</el-button>
            <el-button :icon="FolderChecked" @click="activeSubMenu = '模型列表'">开始建模</el-button>
          </div>
        </div>

        <div class="home-flow">
          <article v-for="step in homeSteps" :key="step.title" class="home-step">
            <strong>{{ step.index }}</strong>
            <h3>{{ step.title }}</h3>
            <p>{{ step.description }}</p>
          </article>
        </div>

        <div class="home-grid">
          <article v-for="card in homeCards" :key="card.title" class="home-card">
            <span>{{ card.tag }}</span>
            <h3>{{ card.title }}</h3>
            <p>{{ card.description }}</p>
            <el-button size="small" text @click="activeSubMenu = card.target">{{ card.action }}</el-button>
          </article>
        </div>
      </section>

      <template v-else-if="activeModule === '数据源管理'">
        <aside class="left-panel">
          <section class="panel-section">
            <div class="section-title">
              <span>数据源列表</span>
              <el-tag size="small" type="info">{{ dataSources.length }}</el-tag>
            </div>
            <div class="data-source-list">
              <button
                v-for="item in dataSources"
                :key="item.id"
                :class="['data-source-item', { active: selectedDataSourceId === item.id }]"
                @click="selectedDataSourceId = item.id"
              >
                <span>{{ item.name }}</span>
                <el-tag size="small" :type="dataSourceStatusType(item.status)">{{ dataSourceStatusText[item.status] }}</el-tag>
              </button>
            </div>
          </section>
        </aside>

        <section class="management-content data-source-main">
          <section v-if="activeSubMenu === '数据源列表'" class="content-panel">
            <div class="content-header">
              <div>
                <h2>数据源列表</h2>
                <p>在列表中完成数据源新增、编辑、删除和连接测试。</p>
              </div>
              <el-button type="primary" :icon="Plus" @click="startCreateDataSource">新建数据源</el-button>
            </div>

            <div class="list-toolbar">
              <el-input v-model="dataSourceKeyword" placeholder="搜索名称 / 类型 / JDBC URL" :prefix-icon="Search" clearable />
            </div>

            <div class="inline-editor" v-if="editingDataSource">
              <div class="inline-editor-title">
                <strong>{{ editingDataSourceId ? '编辑数据源' : '新建数据源' }}</strong>
                <el-button size="small" text @click="cancelEditDataSource">取消</el-button>
              </div>
              <div class="source-form compact">
                <label class="form-row">
                  <span>数据源名称</span>
                  <el-input v-model="dataSourceForm.name" placeholder="例如 mysql-dev" />
                </label>
                <label class="form-row">
                  <span>数据库类型</span>
                  <el-select v-model="dataSourceForm.databaseType">
                    <el-option label="MySQL" value="MySQL" />
                    <el-option label="PostgreSQL" value="PostgreSQL" />
                    <el-option label="H2" value="H2" />
                  </el-select>
                </label>
                <label class="form-row wide">
                  <span>JDBC URL</span>
                  <el-input v-model="dataSourceForm.jdbcUrl" placeholder="jdbc:mysql://127.0.0.1:3306/order_center" />
                </label>
                <label class="form-row">
                  <span>用户名</span>
                  <el-input v-model="dataSourceForm.username" />
                </label>
                <label class="form-row">
                  <span>密码</span>
                  <el-input v-model="dataSourceForm.password" type="password" show-password />
                </label>
                <label class="form-row wide">
                  <span>说明</span>
                  <el-input v-model="dataSourceForm.description" type="textarea" :rows="2" />
                </label>
              </div>
              <div class="content-actions">
                <el-button type="primary" :icon="Plus" @click="submitDataSource">{{ editingDataSourceId ? '保存修改' : '保存数据源' }}</el-button>
                <el-button @click="resetDataSourceForm">重置</el-button>
              </div>
            </div>

            <div class="data-source-table">
              <div class="table-head">
                <span>名称</span>
                <span>类型</span>
                <span>连接状态</span>
                <span>表数量</span>
                <span>最近采集</span>
                <span>操作</span>
              </div>
              <div
                v-for="item in filteredDataSources"
                :key="item.id"
                :class="['table-line', { active: selectedDataSourceId === item.id }]"
                @click="selectedDataSourceId = item.id"
              >
                <strong>{{ item.name }}</strong>
                <span>{{ item.databaseType }}</span>
                <el-tag size="small" :type="dataSourceStatusType(item.status)">{{ dataSourceStatusText[item.status] }}</el-tag>
                <span>{{ item.tableCount }}</span>
                <span>{{ formatTime(item.lastCollectedAt) }}</span>
                <div class="row-actions">
                  <el-button size="small" text :icon="Connection" @click.stop="runConnectionTest(item.id)">测试</el-button>
                  <el-button size="small" text @click.stop="startEditDataSource(item)">编辑</el-button>
                  <el-button size="small" text type="danger" @click.stop="removeDataSource(item.id)">删除</el-button>
                </div>
              </div>
            </div>
            <div v-if="operationMessage" class="operation-message">{{ operationMessage }}</div>
          </section>

          <section v-else class="content-panel">
            <div class="content-header">
              <div>
                <h2>元数据采集</h2>
                <p>读取数据源中的表、字段、主键和候选外键信息。</p>
              </div>
              <el-button type="primary" :icon="Refresh" :disabled="!selectedDataSourceRecord" @click="runMetadataCollect">
                开始采集
              </el-button>
            </div>
            <div v-if="metadataResult" class="metadata-summary">
              <span>表 {{ metadataResult.tableCount }} 张</span>
              <span>字段 {{ metadataResult.columnCount }} 个</span>
              <span>采集时间 {{ formatTime(metadataResult.collectedAt) }}</span>
            </div>
            <div class="metadata-toolbar">
              <el-input
                v-model="metadataKeyword"
                clearable
                placeholder="筛选表名 / 注释"
                :prefix-icon="Search"
                @keyup.enter="searchMetadataTables"
                @clear="searchMetadataTables"
              />
              <el-button :icon="Search" @click="searchMetadataTables">筛选</el-button>
              <el-button
                type="danger"
                plain
                :disabled="!selectedMetadataTables.length"
                @click="removeSelectedMetadataTables"
              >
                批量删除
              </el-button>
            </div>
            <div class="metadata-table">
              <el-table
                :data="metadataTablePage?.tables || []"
                row-key="id"
                v-loading="metadataLoading"
                empty-text="暂无元数据表"
                @selection-change="handleMetadataSelectionChange"
              >
                <el-table-column type="selection" width="44" />
                <el-table-column type="expand">
                  <template #default="{ row }">
                    <div class="metadata-column-table">
                      <div class="metadata-column-head">
                        <span>字段名</span>
                        <span>类型</span>
                        <span>键</span>
                        <span>注释</span>
                      </div>
                      <div v-for="column in row.columns" :key="column.name" class="metadata-column-line">
                        <strong>{{ column.name }}</strong>
                        <span>{{ column.type }}</span>
                        <span>
                          <el-tag v-if="column.primaryKey" size="small" type="success">PK</el-tag>
                          <el-tag v-if="column.foreignKey" size="small" type="warning">FK</el-tag>
                          <em v-if="!column.primaryKey && !column.foreignKey">-</em>
                        </span>
                        <span>{{ column.comment || '-' }}</span>
                      </div>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column prop="name" label="表名" min-width="180" show-overflow-tooltip />
                <el-table-column prop="comment" label="注释" min-width="180" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.comment || '-' }}</template>
                </el-table-column>
                <el-table-column prop="columnCount" label="字段数" width="90" />
                <el-table-column label="操作" width="120" fixed="right">
                  <template #default="{ row }">
                    <el-button size="small" text type="danger" @click="removeMetadataTable(row.id, row.name)">删除表</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <div class="metadata-pagination">
                <el-pagination
                  v-model:current-page="metadataPage"
                  v-model:page-size="metadataPageSize"
                  :page-sizes="[5, 10, 20, 50]"
                  :total="metadataTablePage?.total || 0"
                  layout="total, sizes, prev, pager, next"
                  @size-change="loadMetadataTablePage"
                  @current-change="loadMetadataTablePage"
                />
              </div>
            </div>
          </section>
        </section>
      </template>

      <template v-else>
      <template v-if="activeSubMenu === '模型列表'">
        <section class="management-content model-list-main">
          <section class="content-panel">
            <div class="content-header">
              <div>
                <h2>模型列表</h2>
                <p>管理基于数据源元数据创建的关系模型。</p>
              </div>
              <el-button type="primary" :icon="Plus" @click="startCreateRelationModel">新增模型</el-button>
            </div>
            <div class="list-toolbar">
              <el-input
                v-model="relationModelKeyword"
                clearable
                placeholder="搜索模型名称 / 数据源 / 说明"
                :prefix-icon="Search"
                @keyup.enter="searchRelationModels"
                @clear="searchRelationModels"
              />
              <el-button :icon="Search" @click="searchRelationModels">筛选</el-button>
            </div>
            <div class="relation-model-table">
              <el-table
                :data="relationModelPage?.models || []"
                row-key="id"
                v-loading="relationModelLoading"
                empty-text="暂无关系模型"
                :expand-row-keys="relationModelExpandedKeys"
                @expand-change="handleRelationModelExpandChange"
              >
                <el-table-column type="expand" width="48">
                  <template #default="{ row }">
                    <div class="model-table-accordion">
                      <strong>包含表</strong>
                      <div class="model-table-tags">
                        <el-tag
                          v-for="tableName in tableNamesOf(row)"
                          :key="tableName"
                          size="small"
                          type="info"
                          effect="plain"
                        >
                          {{ tableName }}
                        </el-tag>
                        <span v-if="!tableNamesOf(row).length" class="empty-cell">暂无表</span>
                      </div>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column prop="name" label="模型名称" min-width="180" show-overflow-tooltip />
                <el-table-column prop="nodeCount" label="表数量" width="100" />
                <el-table-column prop="edgeCount" label="关系数量" width="100" />
                <el-table-column prop="dataSourceName" label="引用数据源" min-width="150" show-overflow-tooltip />
                <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.description || '-' }}</template>
                </el-table-column>
                <el-table-column label="操作" width="180" fixed="right">
                  <template #default="{ row }">
                    <el-button size="small" type="primary" plain @click.stop="openRelationModel(row.id)">编辑模型</el-button>
                    <el-button size="small" type="danger" plain @click.stop="removeRelationModel(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <div class="metadata-pagination">
                <el-pagination
                  v-model:current-page="relationModelPageNo"
                  v-model:page-size="relationModelPageSize"
                  :page-sizes="[5, 10, 20, 50]"
                  :total="relationModelPage?.total || 0"
                  layout="total, sizes, prev, pager, next"
                  @size-change="loadRelationModelPage"
                  @current-change="loadRelationModelPage"
                />
              </div>
            </div>
          </section>
        </section>
      </template>

      <template v-else-if="activeSubMenu === '关联数据查询'">
        <section class="management-content relation-query-main">
          <section class="content-panel">
            <div class="content-header">
              <div>
                <h2>关联数据查询</h2>
                <p>选择关系模型中的表和字段，输入值后沿模型关系查询关联数据。</p>
              </div>
              <el-button type="primary" :icon="Search" :loading="relationQueryLoading" @click="runRelationDataQuery">
                查询
              </el-button>
            </div>

            <div class="relation-query-form">
              <label class="form-row">
                <span>关系模型</span>
                <el-select v-model="queryRelationModelId" filterable placeholder="选择模型" @change="handleQueryModelChange">
                  <el-option
                    v-for="model in queryRelationModels"
                    :key="model.id"
                    :label="model.name"
                    :value="model.id"
                  />
                </el-select>
              </label>
              <label class="form-row">
                <span>查询表</span>
                <el-select v-model="queryTableName" filterable placeholder="选择表" :disabled="!queryModelDetail" @change="handleQueryTableChange">
                  <el-option
                    v-for="node in queryTableOptions"
                    :key="node.tableName"
                    :label="node.tableName"
                    :value="node.tableName"
                  />
                </el-select>
              </label>
              <label class="form-row">
                <span>查询字段</span>
                <el-select v-model="queryColumnName" filterable placeholder="选择字段" :disabled="!queryTableName">
                  <el-option
                    v-for="column in queryColumnOptions"
                    :key="column.name"
                    :label="fieldOptionLabel(column)"
                    :value="column.name"
                  />
                </el-select>
              </label>
              <label class="form-row">
                <span>字段值</span>
                <el-input v-model="queryColumnValue" clearable placeholder="输入要匹配的值" @keyup.enter="runRelationDataQuery" />
              </label>
            </div>

            <div v-if="relationQueryResult" class="relation-query-summary">
              <span>命中表 {{ relationQueryResult.tables.length }} 张</span>
              <span>总行数 {{ relationQueryResult.tables.reduce((total, table) => total + table.rowCount, 0) }}</span>
            </div>

            <el-collapse v-if="relationQueryResult" v-model="relationQueryActivePanels" class="relation-query-accordion">
              <el-collapse-item
                v-for="table in relationQueryResult.tables"
                :key="table.tableName"
                :name="table.tableName"
              >
                <template #title>
                  <div class="accordion-title">
                    <strong>{{ table.tableName }}</strong>
                    <span>{{ table.comment || table.relationPath }}</span>
                    <el-tag size="small" type="info">{{ table.rowCount }} 行</el-tag>
                  </div>
                </template>
                <div class="accordion-meta">{{ table.relationPath }}</div>
                <el-table :data="table.rows" size="small" border max-height="340" empty-text="暂无数据">
                  <el-table-column
                    v-for="column in table.columns"
                    :key="column"
                    :prop="column"
                    :label="column"
                    min-width="140"
                    show-overflow-tooltip
                  >
                    <template #default="{ row }">{{ formatCellValue(row[column]) }}</template>
                  </el-table-column>
                </el-table>
              </el-collapse-item>
            </el-collapse>
            <p v-else class="empty-hint">选择模型、表、字段和值后开始查询，结果会按关联表折叠展示。</p>
          </section>
        </section>
      </template>

      <template v-else-if="activeModule === 'AI 问数'">
        <section class="management-content ai-ask-main">
          <section class="ai-ask-layout">
            <aside class="ai-ask-context">
              <section class="context-block">
                <div class="section-title">
                  <span>当前模型</span>
                  <el-tag size="small" type="success">{{ aiAskModelEntities.length }} 个实体</el-tag>
                </div>
                <el-select v-model="aiAskRelationModelId" filterable placeholder="选择关系模型" @change="handleAiAskModelChange">
                  <el-option
                    v-for="model in queryRelationModels"
                    :key="model.id"
                    :label="model.name"
                    :value="model.id"
                  />
                </el-select>
                <p class="context-note">{{ aiAskModelDetail?.description || selectedAiAskModel?.description || '先选择一个关系模型，AI 会基于模型实体、关系和字段能力回答问题。' }}</p>
              </section>

              <section class="context-block">
                <div class="section-title">
                  <span>模型理解</span>
                  <div class="model-summary-actions">
                    <el-tag size="small" type="info">{{ aiAskModelRelations.length }} 条关系</el-tag>
                    <el-tooltip content="AI 解读说明" placement="top">
                      <el-button
                        size="small"
                        circle
                        :icon="MagicStick"
                        :loading="aiAskSummaryGenerating"
                        :disabled="!aiAskModelDetail"
                        @click="generateAiAskModelSummary"
                      />
                    </el-tooltip>
                  </div>
                </div>
                <div class="model-summary">
                  <el-input
                    v-model="aiAskModelSummary"
                    type="textarea"
                    :rows="5"
                    resize="none"
                    placeholder="选择模型后，可由 AI 解读模型关系，也可以在这里人工补充说明。"
                  />
                </div>
              </section>

              <section class="context-block">
                <div class="agent-rule-editor">
                  <div class="rule-editor-title">
                    <span>模型提示词</span>
                    <el-tag size="small" type="info">{{ aiAskRules.length }}</el-tag>
                  </div>
                  <el-input
                    v-model="aiAskRuleDraft"
                    type="textarea"
                    :rows="2"
                    resize="none"
                    placeholder="例如：检查订单明细依赖时，必须优先确认 ORDER_ID 是否指向订单主表。"
                    @keyup.enter.exact.prevent="submitAiAskRule"
                  />
                  <div class="rule-actions">
                    <el-button size="small" type="primary" plain :disabled="!aiAskRuleDraft.trim()" @click="submitAiAskRule">
                      {{ editingAiAskRuleId ? '保存提示词' : '添加提示词' }}
                    </el-button>
                    <el-button v-if="editingAiAskRuleId" size="small" text @click="cancelEditAiAskRule">取消编辑</el-button>
                    <el-button size="small" text @click="resetAiAskRules">恢复默认</el-button>
                  </div>
                  <div class="rule-list">
                    <article v-for="rule in aiAskRules" :key="rule.id" class="rule-item">
                      <p>{{ rule.content }}</p>
                      <div class="rule-item-actions">
                        <el-button size="small" text @click="startEditAiAskRule(rule)">编辑</el-button>
                        <el-button size="small" text type="danger" @click="removeAiAskRule(rule.id)">删除</el-button>
                      </div>
                    </article>
                  </div>
                </div>
              </section>
            </aside>

            <section class="ai-chat-panel">
              <div class="ai-chat-header">
                <div>
                  <h2>AI 问数</h2>
                  <p>围绕生成的关系模型对话，Agent 会先理解模型，再识别实体并规划可执行的数据查询。</p>
                </div>
                <el-tag type="success" effect="plain">流式执行</el-tag>
              </div>

              <div ref="aiChatThreadRef" class="chat-thread">
                <article v-for="message in aiAskMessages" :key="message.id" :class="['chat-message', message.role]">
                  <div class="message-bubble">
                    <p v-if="message.role === 'user' || (!message.toolSteps?.length && !message.result)" class="message-text">
                      {{ message.content }}
                    </p>
                    <div v-if="message.entities?.length" class="message-entities">
                      <el-tag v-for="entity in message.entities" :key="entity" size="small">{{ entity }}</el-tag>
                    </div>
                    <div v-if="message.toolSteps?.length" class="agent-trace">
                      <div v-for="step in message.toolSteps" :key="step.title" class="trace-step">
                        <Check />
                        <div>
                          <strong>{{ step.title }}</strong>
                          <span>{{ step.detail }}</span>
                        </div>
                      </div>
                    </div>
                    <div v-if="message.result && (message.result.sql || message.result.tables.length)" class="mock-result">
                      <div class="mock-result-header">
                        <strong>{{ message.result.title }}</strong>
                        <span>{{ message.result.summary }}</span>
                      </div>
                      <div class="result-table-grid">
                        <article v-for="table in message.result.tables" :key="table.name">
                          <strong>{{ table.name }}</strong>
                          <span>{{ table.rows }} 行</span>
                          <p>{{ table.fields.join(' / ') }}</p>
                        </article>
                      </div>
                      <pre>{{ message.result.sql }}</pre>
                      <el-table
                        v-if="message.result.rows?.length"
                        :data="message.result.rows"
                        size="small"
                        border
                        max-height="220"
                        empty-text="暂无数据"
                      >
                        <el-table-column
                          v-for="column in message.result.columns"
                          :key="column"
                          :prop="column"
                          :label="column"
                          min-width="140"
                          show-overflow-tooltip
                        >
                          <template #default="{ row }">{{ formatCellValue(row[column]) }}</template>
                        </el-table-column>
                      </el-table>
                    </div>
                    <section v-if="message.role === 'assistant' && message.content" class="answer-markdown">
                      <strong class="answer-title">结论</strong>
                      <div v-html="renderMarkdown(message.content)" />
                    </section>
                  </div>
                </article>
              </div>

              <div class="ask-composer">
                <el-input
                  v-model="aiAskQuestion"
                  type="textarea"
                  :rows="2"
                  resize="none"
                  placeholder="例如：查一下订单 1001 的支付记录、商品明细和用户信息"
                  @keyup.enter.exact.prevent="sendAiAskQuestion"
                />
                <div class="composer-actions">
                  <span>{{ aiAskModelDetail ? `已连接模型：${aiAskModelDetail.name}` : '请选择关系模型' }}</span>
                  <el-button type="primary" :icon="Search" :loading="aiAskLoading" :disabled="!aiAskQuestion.trim()" @click="sendAiAskQuestion">
                    发送问题
                  </el-button>
                </div>
              </div>
            </section>
          </section>
        </section>
      </template>

      <template v-else>
        <aside class="left-panel">
          <section class="panel-section model-entry">
            <div class="section-title">
              <span>{{ activeSubMenu === '新增模型' ? '新增模型' : '建模入口' }}</span>
              <el-tag size="small" type="success">关系模型</el-tag>
            </div>
            <label class="form-row">
              <span>模型名称</span>
              <el-input v-model="modelDraft.name" size="small" />
            </label>
            <label class="form-row">
              <span>数据源</span>
              <el-select v-model="modelDraft.dataSourceId" size="small" @change="handleModelDataSourceChange">
                <el-option v-for="item in dataSources" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </label>
            <label class="form-row">
              <span>模型说明</span>
              <el-input v-model="modelDraft.description" size="small" />
            </label>
            <el-button class="analyze-button" type="primary" :icon="FolderChecked" @click="saveRelationModelDraft">
              保存模型
            </el-button>
          </section>

          <section class="panel-section ai-analysis">
            <div class="section-title">
              <span>AI 分析</span>
              <el-tag size="small" type="warning">{{ aiRelationCandidates.length }}</el-tag>
            </div>
            <label class="form-row">
              <span>主表</span>
              <el-select v-model="aiMainTable" size="small" filterable allow-create default-first-option placeholder="输入或选择主表">
                <el-option v-for="table in modelSourceTables" :key="table.name" :label="table.name" :value="table.name" />
              </el-select>
            </label>
            <label class="form-row">
              <span>AI 模型</span>
              <el-select v-model="selectedAiModel" size="small" placeholder="选择分析模型">
                <el-option
                  v-for="model in aiModels"
                  :key="model.id"
                  :label="model.name"
                  :value="model.id"
                  :disabled="!model.enabled"
                >
                  <div class="ai-model-option">
                    <span>{{ model.name }}</span>
                    <em>{{ model.enabled ? model.provider : '未配置' }}</em>
                  </div>
                </el-option>
              </el-select>
            </label>
            <el-button class="analyze-button" type="primary" plain :loading="aiAnalyzing" :disabled="!aiMainTable || !modelDraft.dataSourceId || !selectedAiModel" @click="analyzeMainTableRelations">
              分析关联关系
            </el-button>
            <div class="ai-process" v-if="aiThinkingSteps.length">
              <div class="ai-process-title">
                <span>思考过程</span>
                <el-tag size="small" :type="aiAnalyzing ? 'warning' : 'success'">
                  {{ aiAnalyzing ? '分析中' : '已完成' }}
                </el-tag>
              </div>
              <div class="ai-process-list">
                <div v-for="step in aiThinkingSteps" :key="step.id" class="ai-process-item">
                  <span></span>
                  <p>{{ step.message }}</p>
                </div>
              </div>
            </div>
            <div class="ai-result-list" v-if="aiRelationCandidates.length">
              <article v-for="candidate in aiRelationCandidates" :key="candidate.label" class="ai-result-item">
                <strong>{{ candidate.label }}</strong>
                <p>{{ candidate.reason }}</p>
                <div class="ai-result-meta">
                  <el-tag size="small" type="success">{{ Math.round(candidate.confidence * 100) }}%</el-tag>
                  <el-tag size="small" type="info">{{ candidate.relationType }}</el-tag>
                </div>
              </article>
            </div>
            <p v-else class="empty-hint">输入主表并选择可用模型后，系统会根据模型返回结果生成字段级关联关系。</p>
          </section>
        </aside>

      <section
        ref="canvasRef"
        :class="['canvas-area', 'relation-canvas-area', { fullscreen: canvasFullscreen }]"
        @dragover.prevent
        @drop="dropTableToModelCanvas"
        @pointerdown="startCanvasPan"
        @pointermove="handlePointerMove"
        @pointerup="endPointerInteraction"
        @pointercancel="endPointerInteraction"
        @pointerleave="endPointerInteraction"
      >
        <div class="canvas-toolbar" @pointerdown.stop>
          <el-button :icon="Rank" circle size="small" title="自动布局" @click.stop="autoLayoutModelCanvas()" />
          <el-button :icon="ZoomIn" circle size="small" title="放大" />
          <el-button :icon="ZoomOut" circle size="small" title="缩小" />
          <el-button :icon="FullScreen" circle size="small" :title="canvasFullscreen ? '退出全屏' : '全屏展示'" @click.stop="toggleCanvasFullscreen" />
          <el-button :icon="Aim" circle size="small" title="适配画布" @click.stop="fitCanvas" />
          <el-button :icon="Check" circle size="small" title="确认关系" />
          <el-button :icon="Connection" size="small" title="配置关系" @click.stop="openRelationConfigDialog()">配置关系</el-button>
        </div>

        <div class="canvas-world" :style="worldStyle">
          <svg class="edge-layer" :viewBox="`0 0 ${canvasSize.width} ${canvasSize.height}`">
            <defs>
              <marker id="arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
                <path d="M 0 0 L 8 4 L 0 8 z" fill="#7f93ad" />
              </marker>
            </defs>
            <g v-for="edge in positionedEdges" :key="edge.label">
              <path
                :d="edge.path"
                class="edge-hitbox"
                @pointerdown.stop
                @click.stop="openRelationConfigDialog(edge.label)"
              />
              <path
                :d="edge.path"
                :class="['edge-path', { confirmed: edge.confirmed }]"
                marker-end="url(#arrow)"
                @pointerdown.stop
                @click.stop="openRelationConfigDialog(edge.label)"
              />
              <text
                :x="edge.labelX"
                :y="edge.labelY"
                class="edge-label"
                @pointerdown.stop
                @click.stop="openRelationConfigDialog(edge.label)"
              >
                {{ edge.shortLabel }}
              </text>
            </g>
          </svg>

          <article
            v-for="node in graphNodes"
            :key="node.tableName"
            :class="['table-node', node.status, { dragging: draggedNode === node.tableName }]"
            :style="{ left: `${node.x}px`, top: `${node.y}px` }"
            @pointerdown.stop="startNodeDrag($event, node.tableName)"
            @pointermove="handlePointerMove"
            @pointerup="endPointerInteraction"
            @pointercancel="endPointerInteraction"
          >
            <div class="node-header">
              <div>
                <strong>{{ node.tableName }}</strong>
                <span>{{ node.comment }}</span>
              </div>
              <div class="node-header-actions" @pointerdown.stop @mousedown.stop>
                <el-tag v-if="activeSubMenu !== '关系图编辑' && activeSubMenu !== '新增模型'" size="small" :type="node.status === 'confirmed' ? 'success' : node.status === 'recommended' ? 'warning' : 'info'">
                  {{ statusText[node.status] }}
                </el-tag>
                <el-button
                  size="small"
                  text
                  type="danger"
                  @pointerdown.stop="removeModelNode(node.tableName)"
                  @mousedown.stop="removeModelNode(node.tableName)"
                  @click.stop
                >
                  删除
                </el-button>
              </div>
            </div>
            <div class="node-tools">
              <button
                :class="{ active: relationSourceTable === node.tableName }"
                @pointerdown.stop
                @click.stop="selectRelationSource(node.tableName)"
              >
                设为起点
              </button>
              <button @pointerdown.stop @click.stop="prepareRelationTarget(node.tableName)">连接到此表</button>
            </div>
            <div class="field-list">
              <div
                v-for="column in visibleNodeColumns(node)"
                :key="column.name"
                :class="['field-row', { related: isRelatedNodeColumn(node.tableName, column.name) }]"
              >
                <span class="field-name">
                  <span v-if="column.badge" class="field-badge">{{ column.badge }}</span>
                  {{ column.name }}
                </span>
                <span class="field-comment">{{ column.comment || '-' }}</span>
              </div>
              <div v-if="!visibleNodeColumns(node).length" class="field-empty">暂无关联字段</div>
            </div>
          </article>
        </div>
        </section>

        <aside class="right-panel">
          <section class="panel-section">
            <div class="section-title">
              <span>表资源</span>
              <el-tag size="small" type="info">{{ modelSourceTables.length }}</el-tag>
            </div>
            <el-input v-model="keyword" size="small" placeholder="搜索表名 / 注释" :prefix-icon="Search" />
            <div class="table-list">
              <button
                v-for="table in filteredModelTables"
                :key="table.name"
                class="table-item"
                draggable="true"
                @dragstart="startTableDrag($event, table.name)"
                @click="addTableToModelCanvas(table.name)"
              >
                <Coin class="table-icon" />
                <el-tooltip :content="table.name" placement="top" :show-after="350">
                  <span class="table-name">{{ table.name }}</span>
                </el-tooltip>
                <em class="table-count">{{ table.columns.length }}</em>
              </button>
            </div>
          </section>

        </aside>
      </template>
      </template>
    </main>

    <el-dialog
      v-model="relationDialogVisible"
      :title="editingRelationLabel ? '配置依赖关系' : '批量配置 1 对 N 关系'"
      width="760px"
      class="relation-dialog"
      @closed="resetRelationDialog"
    >
      <div class="relation-dialog-grid">
        <section class="relation-graph-picker">
          <button
            v-for="node in modelCanvasNodes"
            :key="node.tableName"
            :class="[
              'graph-picker-node',
              {
                active: relationConfigSource === node.tableName,
                target: relationConfigTargets.includes(node.tableName),
                disabled: Boolean(relationConfigSource) && node.tableName !== relationConfigSource && isRelationTargetDisabled(node.tableName)
              }
            ]"
            :disabled="Boolean(relationConfigSource) && node.tableName !== relationConfigSource && isRelationTargetDisabled(node.tableName)"
            @click="handleGraphPickerClick(node.tableName)"
          >
            <strong>{{ node.tableName }}</strong>
            <span>{{ node.comment }}</span>
          </button>
          <svg class="dialog-edge-preview" :viewBox="`0 0 ${dialogPreviewSize.width} ${dialogPreviewSize.height}`">
            <defs>
              <marker id="dialog-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
                <path d="M 0 0 L 8 4 L 0 8 z" fill="#2e7fc7" />
              </marker>
            </defs>
            <path
              v-for="target in relationConfigTargets"
              :key="target"
              :d="dialogPreviewPath(target)"
              class="dialog-preview-path"
              marker-end="url(#dialog-arrow)"
            />
          </svg>
        </section>

        <section class="relation-config-form">
          <label class="form-row">
            <span>起点表</span>
            <el-select v-model="relationConfigSource" size="small" placeholder="选择起点表" @change="handleRelationConfigSourceChange">
              <el-option v-for="node in modelCanvasNodes" :key="node.tableName" :label="node.tableName" :value="node.tableName" />
            </el-select>
          </label>
          <label class="form-row">
            <span>起点字段</span>
            <el-select v-model="relationConfigSourceColumn" size="small" placeholder="选择同名字段" :disabled="!relationConfigSource" @change="handleRelationConfigSourceColumnChange">
              <el-option
                v-for="column in relationConfigSourceColumns"
                :key="column.name"
                :label="fieldOptionLabel(column)"
                :value="column.name"
              />
            </el-select>
          </label>
          <label class="form-row">
            <span>连线表</span>
            <el-select
              v-model="relationConfigTargets"
              size="small"
              multiple
              collapse-tags
              collapse-tags-tooltip
              placeholder="选择多个连线表"
              :disabled="!relationConfigSourceColumn || Boolean(editingRelationLabel)"
              @change="handleRelationConfigTargetsChange"
            >
              <el-option
                v-for="node in modelCanvasNodes"
                :key="node.tableName"
                :label="targetOptionLabel(node.tableName)"
                :value="node.tableName"
                :disabled="isRelationTargetDisabled(node.tableName)"
              />
            </el-select>
          </label>
          <div class="target-field-list" v-if="relationConfigTargets.length">
            <div v-for="target in relationConfigTargets" :key="target" class="target-field-line">
              <span>{{ target }}</span>
              <el-select v-model="relationConfigTargetColumns[target]" size="small" placeholder="选择目标字段">
                <el-option
                  v-for="column in targetColumnOptions(target)"
                  :key="column.name"
                  :label="fieldOptionLabel(column)"
                  :value="column.name"
                />
              </el-select>
            </div>
          </div>
          <div class="relation-dialog-actions">
            <el-button v-if="editingRelationLabel" type="danger" plain @click="deleteEditingRelation">删除关系</el-button>
            <el-button @click="relationDialogVisible = false">取消</el-button>
            <el-button type="primary" :disabled="!canSubmitRelationConfig" @click="submitRelationConfig">
              {{ editingRelationLabel ? '保存关系' : '生成关系' }}
            </el-button>
          </div>
        </section>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import {
  Aim,
  Check,
  Coin,
  Connection,
  FolderChecked,
  FullScreen,
  MagicStick,
  Plus,
  Rank,
  Refresh,
  Search,
  ZoomIn,
  ZoomOut
} from '@element-plus/icons-vue'
import {
  collectDataSourceMetadata,
  createDataSource,
  deleteDataSource,
  deleteMetadataTable,
  deleteMetadataTables,
  fetchDataSources,
  fetchMetadataTables,
  testDataSourceConnection,
  updateDataSource
} from '../api/dataSources'
import {
  analyzeRelationsStream,
  chatWithAiQueryStream,
  createModelPrompt,
  deleteRelationModel,
  deleteModelPrompt,
  fetchAiModels,
  fetchAiQueryModelSummary,
  fetchModelPrompts,
  fetchRelationModelDetail,
  fetchRelationModels,
  fetchWorkbenchOverview,
  queryAssociatedData,
  resetModelPrompts,
  saveRelationModel,
  summarizeAiQueryModelStream,
  updateModelPrompt
} from '../api/workbench'
import type {
  DataSourceForm,
  DataSourceItem,
  MetadataCollectResponse,
  MetadataTableItem,
  MetadataTablePage,
  TableMetadata
} from '../types/dataSource'
import type {
  AiModelOption,
  AnalyzeProgressEvent,
  AssociatedDataResponse,
  AiQueryChatExecutionResult,
  CandidateRelation,
  ModelPrompt,
  RelationEdge,
  RelationModelDetail,
  RelationModelListItem,
  RelationModelPage,
  TableNode,
  WorkbenchOverview
} from '../types/workbench'

type AiAskMessage = {
  id: number
  role: 'assistant' | 'user'
  content: string
  entities?: string[]
  toolSteps?: { title: string; detail: string }[]
  result?: {
    title: string
    summary: string
    tables: { name: string; rows: number; fields: string[] }[]
    sql: string
    columns?: string[]
    rows?: Record<string, unknown>[]
    rowCount?: number
  }
}

const overview = ref<WorkbenchOverview>()
const localNodes = ref<TableNode[]>([])
const keyword = ref('')
const selectedDataSource = ref('mysql-dev')
const selectedModel = ref('订单关系模型')
const relationModelPage = ref<RelationModelPage>()
const relationModelKeyword = ref('')
const relationModelPageNo = ref(1)
const relationModelPageSize = ref(10)
const relationModelLoading = ref(false)
const relationModelExpandedKeys = ref<string[]>([])
const queryRelationModels = ref<RelationModelListItem[]>([])
const queryRelationModelId = ref('')
const queryModelDetail = ref<RelationModelDetail>()
const queryTableName = ref('')
const queryColumnName = ref('')
const queryColumnValue = ref('')
const relationQueryLoading = ref(false)
const relationQueryResult = ref<AssociatedDataResponse>()
const relationQueryActivePanels = ref<string[]>([])
const aiAskRelationModelId = ref('')
const aiAskModelDetail = ref<RelationModelDetail>()
const aiAskModelSummary = ref('')
const aiAskSummaryGenerating = ref(false)
const aiAskSummaryAbortController = ref<AbortController>()
const aiAskQuestion = ref('')
const aiAskLoading = ref(false)
const aiAskChatAbortController = ref<AbortController>()
const aiAskMessages = ref<AiAskMessage[]>([])
const aiChatThreadRef = ref<HTMLElement>()
const defaultAiAskRules = [
  '依赖关系优先使用主键和外键字段，字段名相同但语义不一致时需要标记为待确认。',
  '一对多关系应从主实体主键指向明细实体外键，不能反向作为主查询路径。',
  '跨实体查询需要保留关系路径，便于检查每一步依赖是否可追溯。'
]
const aiAskRuleDraft = ref('')
const aiAskRules = ref<ModelPrompt[]>(defaultAiAskRules.map((content, index) => ({ id: String(index + 1), content, sortOrder: index + 1 })))
const editingAiAskRuleId = ref('')
const modelDraft = reactive({
  id: undefined as string | undefined,
  name: '新建关系模型',
  dataSourceId: undefined as number | undefined,
  description: ''
})
const modelCanvasNodes = ref<TableNode[]>([])
const modelCanvasEdges = ref<RelationEdge[]>([])
const draggedTableName = ref('')
const relationSourceTable = ref('')
const aiMainTable = ref('')
const aiRelationCandidates = ref<RelationEdge[]>([])
const aiModels = ref<AiModelOption[]>([])
const selectedAiModel = ref('')
const aiAnalyzing = ref(false)
const aiThinkingSteps = ref<{ id: number; message: string; timestamp: number }[]>([])
const aiAnalysisAbortController = ref<AbortController>()
const manualRelationSource = ref('')
const manualRelationSourceColumn = ref('')
const manualRelationTarget = ref('')
const manualRelationTargetColumn = ref('')
const relationDialogVisible = ref(false)
const editingRelationLabel = ref('')
const relationConfigSource = ref('')
const relationConfigSourceColumn = ref('')
const relationConfigTargets = ref<string[]>([])
const relationConfigTargetColumns = reactive<Record<string, string>>({})
const dataSources = ref<DataSourceItem[]>([])
const selectedDataSourceId = ref<number>()
const dataSourceKeyword = ref('')
const editingDataSource = ref(false)
const editingDataSourceId = ref<number>()
const dataSourceForm = reactive<DataSourceForm>({
  name: '',
  databaseType: 'MySQL',
  jdbcUrl: '',
  username: '',
  password: '',
  description: ''
})
const metadataResult = ref<MetadataCollectResponse>()
const metadataTablePage = ref<MetadataTablePage>()
const selectedMetadataTables = ref<MetadataTableItem[]>([])
const metadataKeyword = ref('')
const metadataPage = ref(1)
const metadataPageSize = ref(10)
const metadataLoading = ref(false)
const operationMessage = ref('')
const canvasRef = ref<HTMLElement>()
const canvasSize = { width: 2400, height: 1800 }
const dialogPreviewSize = { width: 300, height: 240 }
const nodeSize = { width: 224, height: 170 }
const pan = reactive({ x: 0, y: 0 })
const pointerStart = reactive({ x: 0, y: 0 })
const panStart = reactive({ x: 0, y: 0 })
const nodeStart = reactive({ x: 0, y: 0 })
const draggedNode = ref('')
const isPanning = ref(false)
const canvasFullscreen = ref(false)
let pendingPointerFrame = 0
let pendingPointerEvent: PointerEvent | undefined
const activeSubMenu = ref('项目概览')
const menuGroups = [
  {
    title: '首页',
    items: ['项目概览']
  },
  {
    title: '数据源管理',
    items: ['数据源列表', '元数据采集']
  },
  {
    title: '关系模型',
    items: ['模型列表', '关系图编辑', '关联数据查询']
  },
  {
    title: '数据同步',
    items: ['同步任务', 'SQL 脚本预览', '执行记录']
  },
  {
    title: 'AI 问数',
    items: []
  }
]
const homeSteps = [
  {
    index: '01',
    title: '接入数据源',
    description: '创建数据库连接，完成连接测试，并采集表、字段、主键和候选外键信息。'
  },
  {
    index: '02',
    title: '创建模型',
    description: '在模型列表新增关系模型，选择数据源后从表资源中拖入需要建模的实体。'
  },
  {
    index: '03',
    title: '编辑关系图',
    description: '在图上确认、删除或补充关系，形成可复用的业务关系模型。'
  },
  {
    index: '04',
    title: '查询和生成 SQL',
    description: '基于模型进行关联查询、AI 问数和同步 SQL 预览。'
  }
]
const homeCards = [
  {
    tag: '数据接入',
    title: '数据源管理',
    description: '维护数据库连接，执行测试和元数据采集。',
    action: '进入数据源列表',
    target: '数据源列表'
  },
  {
    tag: '关系建模',
    title: '关系模型',
    description: '基于已采集的元数据创建模型，在画布中拖入表并配置关系。',
    action: '进入建模工作台',
    target: '模型列表'
  },
  {
    tag: '分析使用',
    title: 'AI 问数',
    description: '围绕已确认的模型生成 SQL 和分析结果。',
    action: '查看问数模块',
    target: 'AI 问数'
  }
]

const dataSourceStatusText = {
  CONNECTED: '已连接',
  FAILED: '失败',
  NOT_TESTED: '未测试'
}

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

const modelSource = computed(() => {
  return dataSources.value.find((item) => item.id === modelDraft.dataSourceId)
})

const modelSourceTables = computed(() => {
  return modelSource.value?.metadata || []
})

const filteredModelTables = computed(() => {
  const tables = modelSourceTables.value
  if (!keyword.value) {
    return tables
  }
  const search = keyword.value.toLowerCase()
  return tables.filter((table) => {
    return table.name.toLowerCase().includes(search) || table.comment.toLowerCase().includes(search)
  })
})

const queryTableOptions = computed(() => {
  return queryModelDetail.value?.nodes || []
})

const queryColumnOptions = computed(() => {
  return queryTableOptions.value.find((node) => node.tableName === queryTableName.value)?.columns || []
})

const selectedAiAskModel = computed(() => {
  return queryRelationModels.value.find((model) => model.id === aiAskRelationModelId.value)
})

const aiAskModelEntities = computed(() => {
  return (aiAskModelDetail.value?.nodes || []).map((node) => {
    const keyFields = node.columns
      .filter((column) => column.badge === 'PK' || column.badge === 'FK')
      .map((column) => column.name)
      .slice(0, 4)
    const fallbackFields = node.columns.slice(0, 3).map((column) => column.name)
    return {
      name: node.tableName,
      comment: node.comment || '业务实体',
      keyFields: keyFields.length ? keyFields : fallbackFields,
      capability: buildEntityCapability(node)
    }
  })
})

const aiAskModelRelations = computed(() => aiAskModelDetail.value?.edges || [])

const graphNodes = computed(() => {
  return activeModule.value === '关系模型' ? modelCanvasNodes.value : localNodes.value
})

const activeModule = computed(() => {
  return menuGroups.find((group) => group.title === activeSubMenu.value || group.items.includes(activeSubMenu.value))?.title || '关系模型'
})

const selectedDataSourceRecord = computed(() => {
  return dataSources.value.find((item) => item.id === selectedDataSourceId.value)
})

const filteredDataSources = computed(() => {
  const keyword = dataSourceKeyword.value.trim().toLowerCase()
  if (!keyword) {
    return dataSources.value
  }
  return dataSources.value.filter((item) => {
    return [item.name, item.databaseType, item.jdbcUrl, item.username, item.description]
      .some((value) => value?.toLowerCase().includes(keyword))
  })
})

const canCreateManualRelation = computed(() => {
  return Boolean(
    manualRelationSource.value &&
    manualRelationSourceColumn.value &&
    manualRelationTarget.value &&
    manualRelationTargetColumn.value &&
    manualRelationSource.value !== manualRelationTarget.value
  )
})

const manualSourceColumns = computed(() => {
  return getModelNodeColumns(manualRelationSource.value)
})

const manualTargetColumns = computed(() => {
  return getModelNodeColumns(manualRelationTarget.value)
})

const relationConfigSourceColumns = computed(() => {
  const columns = getModelNodeColumns(relationConfigSource.value)
  if (editingRelationLabel.value) {
    return columns
  }
  return columns.filter((column) => hasCompatibleTargetField(relationConfigSource.value, column.name))
})

const canSubmitRelationConfig = computed(() => {
  return Boolean(
    relationConfigSource.value &&
    relationConfigSourceColumn.value &&
    relationConfigTargets.value.length &&
    relationConfigTargets.value.every((target) => relationConfigTargetColumns[target])
  )
})

const worldStyle = computed(() => ({
  width: `${canvasSize.width}px`,
  height: `${canvasSize.height}px`,
  transform: `translate(${pan.x}px, ${pan.y}px)`
}))

const positionedEdges = computed(() => {
  if (!overview.value && activeModule.value !== '关系模型') {
    return []
  }
  const edges = activeModule.value === '关系模型' ? modelCanvasEdges.value : overview.value?.edges || []
  const nodeMap = new Map(graphNodes.value.map((node) => [node.tableName, node]))
  return edges.map((edge) => {
    const source = nodeMap.get(edge.source)
    const target = nodeMap.get(edge.target)
    if (!source || !target) {
      return { ...edge, path: '', labelX: 0, labelY: 0, shortLabel: edge.label }
    }
    const sourceCenterX = source.x + nodeSize.width / 2
    const sourceCenterY = source.y + nodeSize.height / 2
    const targetCenterX = target.x + nodeSize.width / 2
    const targetCenterY = target.y + nodeSize.height / 2
    const horizontal = Math.abs(targetCenterX - sourceCenterX) >= Math.abs(targetCenterY - sourceCenterY)
    const sx = horizontal
      ? source.x + (targetCenterX >= sourceCenterX ? nodeSize.width : 0)
      : sourceCenterX
    const sy = horizontal
      ? sourceCenterY
      : source.y + (targetCenterY >= sourceCenterY ? nodeSize.height : 0)
    const tx = horizontal
      ? target.x + (targetCenterX >= sourceCenterX ? 0 : nodeSize.width)
      : targetCenterX
    const ty = horizontal
      ? targetCenterY
      : target.y + (targetCenterY >= sourceCenterY ? 0 : nodeSize.height)
    const controlOffset = horizontal ? Math.max(90, Math.abs(tx - sx) * 0.45) : Math.max(80, Math.abs(ty - sy) * 0.45)
    const c1x = horizontal ? sx + (tx >= sx ? controlOffset : -controlOffset) : sx
    const c1y = horizontal ? sy : sy + (ty >= sy ? controlOffset : -controlOffset)
    const c2x = horizontal ? tx - (tx >= sx ? controlOffset : -controlOffset) : tx
    const c2y = horizontal ? ty : ty - (ty >= sy ? controlOffset : -controlOffset)
    return {
      ...edge,
      path: `M ${sx} ${sy} C ${c1x} ${c1y}, ${c2x} ${c2y}, ${tx} ${ty}`,
      labelX: (sx + tx) / 2 - 58,
      labelY: (sy + ty) / 2 - 8,
      shortLabel: edge.label.replace('ORDER_INFO.', '').replace('ORDER_ITEM.', '').replace('PAYMENT_RECORD.', '')
    }
  })
})

function fitCanvas() {
  const rect = canvasRef.value?.getBoundingClientRect()
  if (!rect) {
    return
  }
  pan.x = Math.min(28, Math.max(rect.width - canvasSize.width - 28, (rect.width - canvasSize.width) / 2))
  pan.y = Math.min(28, Math.max(rect.height - canvasSize.height - 28, (rect.height - canvasSize.height) / 2))
}

async function toggleCanvasFullscreen() {
  const target = canvasRef.value
  if (!target) {
    return
  }
  try {
    if (document.fullscreenElement === target) {
      await document.exitFullscreen()
      canvasFullscreen.value = false
    } else {
      await target.requestFullscreen()
      canvasFullscreen.value = true
    }
    await nextTick()
    fitCanvas()
  } catch {
    ElMessage.error('无法切换全屏展示')
  }
}

function syncCanvasFullscreenState() {
  canvasFullscreen.value = document.fullscreenElement === canvasRef.value
}

function startCanvasPan(event: PointerEvent) {
  if (event.button !== 0) {
    return
  }
  isPanning.value = true
  pointerStart.x = event.clientX
  pointerStart.y = event.clientY
  panStart.x = pan.x
  panStart.y = pan.y
  canvasRef.value?.setPointerCapture(event.pointerId)
}

function startNodeDrag(event: PointerEvent, tableName: string) {
  if (event.button !== 0) {
    return
  }
  const node = currentNodeList().find((item) => item.tableName === tableName)
  if (!node) {
    return
  }
  draggedNode.value = tableName
  pointerStart.x = event.clientX
  pointerStart.y = event.clientY
  nodeStart.x = node.x
  nodeStart.y = node.y
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
}

function handlePointerMove(event: PointerEvent) {
  pendingPointerEvent = event
  if (pendingPointerFrame) {
    return
  }
  pendingPointerFrame = window.requestAnimationFrame(() => {
    pendingPointerFrame = 0
    if (pendingPointerEvent) {
      applyPointerMove(pendingPointerEvent)
      pendingPointerEvent = undefined
    }
  })
}

function applyPointerMove(event: PointerEvent) {
  const dx = event.clientX - pointerStart.x
  const dy = event.clientY - pointerStart.y
  if (draggedNode.value) {
    const node = currentNodeList().find((item) => item.tableName === draggedNode.value)
    if (!node) {
      return
    }
    node.x = Math.max(0, Math.min(canvasSize.width - nodeSize.width, nodeStart.x + dx))
    node.y = Math.max(0, Math.min(canvasSize.height - nodeSize.height, nodeStart.y + dy))
    return
  }
  if (isPanning.value) {
    pan.x = panStart.x + dx
    pan.y = panStart.y + dy
  }
}

function currentNodeList() {
  return activeModule.value === '关系模型' ? modelCanvasNodes.value : localNodes.value
}

function endPointerInteraction() {
  draggedNode.value = ''
  isPanning.value = false
  pendingPointerEvent = undefined
  if (pendingPointerFrame) {
    window.cancelAnimationFrame(pendingPointerFrame)
    pendingPointerFrame = 0
  }
}

function startCreateRelationModel() {
  activeSubMenu.value = '新增模型'
  modelDraft.id = undefined
  modelDraft.name = '新建关系模型'
  modelDraft.dataSourceId = dataSources.value[0]?.id
  modelDraft.description = ''
  modelCanvasNodes.value = []
  modelCanvasEdges.value = []
  relationSourceTable.value = ''
  aiMainTable.value = modelDraft.dataSourceId ? modelSourceTables.value[0]?.name || '' : ''
  aiRelationCandidates.value = []
  aiThinkingSteps.value = []
  resetManualRelationForm()
}

async function openRelationModel(id: string) {
  const model = await fetchRelationModelDetail(id)
  activeSubMenu.value = '关系图编辑'
  modelDraft.id = model.id
  modelDraft.name = model.name
  modelDraft.dataSourceId = model.dataSourceId
  modelDraft.description = model.description
  modelCanvasNodes.value = model.nodes.map((node) => ({ ...node, columns: node.columns.map((column) => ({ ...column })) }))
  modelCanvasEdges.value = model.edges.map((edge) => ({ ...edge }))
  relationSourceTable.value = ''
  aiMainTable.value = modelSourceTables.value[0]?.name || ''
  aiRelationCandidates.value = []
  aiThinkingSteps.value = []
  resetManualRelationForm()
  await nextTick()
  fitCanvas()
}

async function saveRelationModelDraft() {
  if (activeModule.value !== '关系模型') {
    return
  }
  if (!modelDraft.name.trim() || !modelDraft.dataSourceId) {
    ElMessage.warning('请填写模型名称并选择数据源')
    return
  }
  const saved = await saveRelationModel({
    id: modelDraft.id,
    name: modelDraft.name,
    dataSourceId: modelDraft.dataSourceId || 0,
    description: modelDraft.description || '未填写说明',
    nodes: modelCanvasNodes.value,
    edges: modelCanvasEdges.value
  })
  modelDraft.id = saved.id
  modelDraft.name = saved.name
  modelDraft.dataSourceId = saved.dataSourceId
  modelDraft.description = saved.description
  await loadRelationModelPage()
  await loadQueryRelationModels()
  ElMessage.success('关系模型已保存')
}

async function removeRelationModel(row: RelationModelListItem) {
  try {
    await ElMessageBox.confirm(`确认删除关系模型「${row.name}」？删除后模型中的表和关系配置也会一并移除。`, '删除模型', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await deleteRelationModel(row.id)
  relationModelExpandedKeys.value = relationModelExpandedKeys.value.filter((id) => id !== row.id)
  if (modelDraft.id === row.id) {
    modelDraft.id = undefined
    modelDraft.name = '新建关系模型'
    modelDraft.description = ''
    modelCanvasNodes.value = []
    modelCanvasEdges.value = []
    aiRelationCandidates.value = []
    aiThinkingSteps.value = []
    resetManualRelationForm()
  }
  if (queryRelationModelId.value === row.id) {
    queryRelationModelId.value = ''
    queryModelDetail.value = undefined
    queryTableName.value = ''
    queryColumnName.value = ''
    relationQueryResult.value = undefined
    relationQueryActivePanels.value = []
  }
  if (aiAskRelationModelId.value === row.id) {
    aiAskRelationModelId.value = ''
    aiAskModelDetail.value = undefined
  }
  await loadRelationModelPage()
  await loadQueryRelationModels()
  ElMessage.success('关系模型已删除')
}

function handleModelDataSourceChange() {
  modelCanvasNodes.value = []
  modelCanvasEdges.value = []
  relationSourceTable.value = ''
  aiMainTable.value = modelSourceTables.value[0]?.name || ''
  aiRelationCandidates.value = []
  aiThinkingSteps.value = []
  resetManualRelationForm()
}

async function analyzeMainTableRelations() {
  const mainTable = modelSourceTables.value.find((table) => table.name === aiMainTable.value)
  if (!mainTable || !modelDraft.dataSourceId) {
    ElMessage.warning('请先选择已采集元数据的数据源和主表')
    return
  }
  aiAnalysisAbortController.value?.abort()
  aiAnalysisAbortController.value = new AbortController()
  aiAnalyzing.value = true
  aiRelationCandidates.value = []
  aiThinkingSteps.value = []
  try {
    await analyzeRelationsStream(
      {
        dataSourceId: String(modelDraft.dataSourceId),
        mainTable: mainTable.name,
        model: selectedAiModel.value
      },
      {
        onProgress: appendAiThinkingStep,
        onRelation: (relation) => appendAnalyzedRelation(mainTable.name, candidateRelationToEdge(relation)),
        onResult: (result) => applyAnalyzedRelations(mainTable.name, result.relations.map(candidateRelationToEdge)),
        onError: (message) => ElMessage.error(message || 'AI 分析失败')
      },
      aiAnalysisAbortController.value.signal
    )
  } catch (error) {
    if ((error as Error).name !== 'AbortError') {
      ElMessage.error((error as Error).message || 'AI 分析失败，请查看后端日志')
    }
  } finally {
    aiAnalyzing.value = false
    aiAnalysisAbortController.value = undefined
  }
}

function appendAiThinkingStep(event: AnalyzeProgressEvent) {
  aiThinkingSteps.value.push({
    id: event.timestamp + aiThinkingSteps.value.length,
    message: event.message,
    timestamp: event.timestamp
  })
}

function applyAnalyzedRelations(mainTableName: string, candidates: RelationEdge[]) {
  aiRelationCandidates.value = candidates
  if (!candidates.length) {
    ElMessage.warning('未识别到与主表相关的候选关系')
    return
  }
  addTableToModelCanvas(mainTableName, 110, 120)
  candidates.forEach((candidate, index) => {
    addTableToModelCanvas(candidate.source, 140 + index * 18, 130 + index * 18)
    addTableToModelCanvas(candidate.target, 140 + index * 18, 130 + index * 18)
    upsertRelationEdge(candidate)
  })
  autoLayoutModelCanvas(mainTableName)
  ElMessage.success(`已分析出 ${candidates.length} 条候选关系`)
  nextTick(fitCanvas)
}

function appendAnalyzedRelation(mainTableName: string, candidate: RelationEdge) {
  if (!aiRelationCandidates.value.some((edge) => edge.label === candidate.label)) {
    aiRelationCandidates.value.push(candidate)
  }
  addTableToModelCanvas(mainTableName, 110, 120)
  addTableToModelCanvas(candidate.source)
  addTableToModelCanvas(candidate.target)
  upsertRelationEdge(candidate)
  autoLayoutModelCanvas(mainTableName)
}

function candidateRelationToEdge(candidate: CandidateRelation): RelationEdge {
  const label = `${candidate.sourceTable}.${candidate.sourceColumn} -> ${candidate.targetTable}.${candidate.targetColumn}`
  return {
    source: candidate.sourceTable,
    target: candidate.targetTable,
    sourceColumn: candidate.sourceColumn,
    targetColumn: candidate.targetColumn,
    label,
    relationType: candidate.relationType,
    confidence: candidate.confidence,
    sourceType: 'AI分析',
    reason: candidate.reason,
    confirmed: false
  }
}

function autoLayoutModelCanvas(rootTable = aiMainTable.value) {
  const nodes = currentNodeList()
  if (!nodes.length) {
    return
  }
  const root = nodes.find((node) => node.tableName === rootTable)?.tableName || nodes[0].tableName
  const levels = buildRelationLevels(root, nodes, currentEdges())
  const horizontalGap = 120
  const verticalGap = 105
  const top = 24
  const maxLevelWidth = Math.max(...levels.map((level) => level.length), 1)
  const contentWidth = maxLevelWidth * nodeSize.width + (maxLevelWidth - 1) * horizontalGap
  const startX = Math.max(24, Math.round((canvasSize.width - contentWidth) / 2))

  levels.forEach((level, levelIndex) => {
    const rowWidth = level.length * nodeSize.width + (level.length - 1) * horizontalGap
    const rowStartX = Math.max(24, startX + Math.round((contentWidth - rowWidth) / 2))
    level.forEach((tableName, index) => {
      const node = nodes.find((item) => item.tableName === tableName)
      if (!node) {
        return
      }
      node.x = Math.max(0, Math.min(canvasSize.width - nodeSize.width, rowStartX + index * (nodeSize.width + horizontalGap)))
      node.y = Math.max(0, Math.min(canvasSize.height - nodeSize.height, top + levelIndex * (nodeSize.height + verticalGap)))
    })
  })
  nextTick(fitCanvas)
}

function buildRelationLevels(root: string, nodes: TableNode[], edges: RelationEdge[]) {
  const nodeNames = nodes.map((node) => node.tableName)
  const nodeSet = new Set(nodeNames)
  const adjacency = new Map<string, string[]>()
  edges.forEach((edge) => {
    if (!nodeSet.has(edge.source) || !nodeSet.has(edge.target)) {
      return
    }
    adjacency.set(edge.source, [...(adjacency.get(edge.source) || []), edge.target])
    adjacency.set(edge.target, [...(adjacency.get(edge.target) || []), edge.source])
  })

  const visited = new Set<string>([root])
  const levels: string[][] = [[root]]
  let cursor = 0
  while (cursor < levels.length) {
    const nextLevel: string[] = []
    levels[cursor].forEach((tableName) => {
      const nextTables = (adjacency.get(tableName) || [])
        .filter((name) => !visited.has(name))
        .sort((left, right) => relationWeight(root, tableName, left, edges) - relationWeight(root, tableName, right, edges))
      nextTables.forEach((name) => {
        visited.add(name)
        nextLevel.push(name)
      })
    })
    if (nextLevel.length) {
      levels.push(nextLevel)
    }
    cursor += 1
  }

  const detached = nodeNames.filter((name) => !visited.has(name))
  if (detached.length) {
    levels.push(detached)
  }
  return levels
}

function relationWeight(root: string, from: string, to: string, edges: RelationEdge[]) {
  const edge = edges.find((item) => {
    return (item.source === from && item.target === to) || (item.source === to && item.target === from)
  })
  if (!edge) {
    return 10
  }
  if (edge.source === root || edge.target === root) {
    return 0
  }
  return edge.confirmed ? 1 : 2
}

function upsertRelationEdge(edge: RelationEdge) {
  if (!modelCanvasEdges.value.some((item) => item.label === edge.label)) {
    modelCanvasEdges.value.push(edge)
  }
}

function dedupeRelations(edges: RelationEdge[]) {
  const seen = new Set<string>()
  return edges.filter((edge) => {
    if (seen.has(edge.label)) {
      return false
    }
    seen.add(edge.label)
    return true
  })
}

function startTableDrag(event: DragEvent, tableName: string) {
  draggedTableName.value = tableName
  event.dataTransfer?.setData('text/plain', tableName)
}

function dropTableToModelCanvas(event: DragEvent) {
  const tableName = event.dataTransfer?.getData('text/plain') || draggedTableName.value
  if (!tableName || activeModule.value !== '关系模型') {
    return
  }
  const rect = canvasRef.value?.getBoundingClientRect()
  const x = rect ? event.clientX - rect.left - pan.x : 120
  const y = rect ? event.clientY - rect.top - pan.y : 120
  addTableToModelCanvas(tableName, x, y)
}

function addTableToModelCanvas(tableName: string, x = 140 + modelCanvasNodes.value.length * 32, y = 120 + modelCanvasNodes.value.length * 42) {
  if (modelCanvasNodes.value.some((node) => node.tableName === tableName)) {
    return
  }
  const table = modelSourceTables.value.find((item) => item.name === tableName)
  if (!table) {
    ElMessage.warning('请先选择已采集元数据的数据源')
    return
  }
  modelCanvasNodes.value.push(tableToNode(table, x, y))
}

function tableToNode(table: TableMetadata, x: number, y: number): TableNode {
  return {
    tableName: table.name,
    comment: table.comment || '数据表',
    status: 'candidate',
    x: Math.max(0, Math.min(canvasSize.width - nodeSize.width, Math.round(x))),
    y: Math.max(0, Math.min(canvasSize.height - nodeSize.height, Math.round(y))),
    columns: table.columns.map((column) => ({
      name: column.name,
      type: column.type,
      badge: column.primaryKey ? 'PK' : column.foreignKey ? 'FK' : '',
      comment: column.comment
    }))
  }
}

function selectRelationSource(tableName: string) {
  relationSourceTable.value = relationSourceTable.value === tableName ? '' : tableName
  manualRelationSource.value = relationSourceTable.value
  manualRelationSourceColumn.value = pickDefaultSourceColumn(manualRelationSource.value)
}

function prepareRelationTarget(targetTable: string) {
  if (!relationSourceTable.value || relationSourceTable.value === targetTable) {
    if (!relationSourceTable.value) {
      ElMessage.warning('请先选择起点表')
    }
    return
  }
  manualRelationSource.value = relationSourceTable.value
  manualRelationSourceColumn.value = manualRelationSourceColumn.value || pickDefaultSourceColumn(manualRelationSource.value)
  manualRelationTarget.value = targetTable
  manualRelationTargetColumn.value = pickDefaultTargetColumn(targetTable, manualRelationSourceColumn.value)
  relationSourceTable.value = ''
  openRelationConfigDialog('', manualRelationSource.value, targetTable)
}

function createManualRelation() {
  if (!canCreateManualRelation.value) {
    return
  }
  addManualRelation(
    manualRelationSource.value,
    manualRelationSourceColumn.value,
    manualRelationTarget.value,
    manualRelationTargetColumn.value
  )
}

function addManualRelation(source: string, sourceColumn: string, target: string, targetColumn: string, relationType = 'CUSTOM') {
  const label = `${source}.${sourceColumn} -> ${target}.${targetColumn}`
  if (!modelCanvasEdges.value.some((edge) => edge.label === label)) {
    modelCanvasEdges.value.push({
      source,
      target,
      sourceColumn,
      targetColumn,
      label,
      relationType,
      confidence: 1,
      sourceType: '手动配置',
      confirmed: true
    })
    ElMessage.success('关系已添加')
  } else {
    ElMessage.warning('关系已存在')
  }
}

function removeModelEdge(label: string) {
  modelCanvasEdges.value = modelCanvasEdges.value.filter((edge) => edge.label !== label)
}

function removeModelNode(tableName: string) {
  if (!modelCanvasNodes.value.some((node) => node.tableName === tableName)) {
    return
  }
  modelCanvasNodes.value = modelCanvasNodes.value.filter((node) => node.tableName !== tableName)
  modelCanvasEdges.value = modelCanvasEdges.value.filter((edge) => edge.source !== tableName && edge.target !== tableName)
  if (relationSourceTable.value === tableName) {
    relationSourceTable.value = ''
  }
  if (manualRelationSource.value === tableName || manualRelationTarget.value === tableName) {
    resetManualRelationForm()
  }
  if (relationConfigSource.value === tableName || relationConfigTargets.value.includes(tableName)) {
    resetRelationDialog()
    relationDialogVisible.value = false
  }
  ElMessage.success('实体已删除')
}

function openRelationConfigDialog(edgeLabel = '', sourceTable = '', targetTable = '') {
  if (!modelCanvasNodes.value.length) {
    ElMessage.warning('请先在关系图中添加表')
    return
  }
  resetRelationDialog()
  const edge = edgeLabel ? modelCanvasEdges.value.find((item) => item.label === edgeLabel) : undefined
  editingRelationLabel.value = edge?.label || ''
  relationConfigSource.value = edge?.source || sourceTable || relationSourceTable.value || modelCanvasNodes.value[0]?.tableName || ''
  relationConfigSourceColumn.value = edge?.sourceColumn || pickDefaultRelationConfigSourceColumn(relationConfigSource.value)
  const initialTarget = edge?.target || targetTable
  if (initialTarget) {
    relationConfigTargets.value = [initialTarget]
    relationConfigTargetColumns[initialTarget] = edge?.targetColumn || pickDefaultRelationConfigTargetColumn(initialTarget)
  }
  relationDialogVisible.value = true
  nextTick(() => {
    if (!relationConfigSourceColumn.value) {
      ElMessage.warning('起点表没有可用于批量配置的同名字段')
    }
  })
}

function resetRelationDialog() {
  editingRelationLabel.value = ''
  relationConfigSource.value = ''
  relationConfigSourceColumn.value = ''
  relationConfigTargets.value = []
  Object.keys(relationConfigTargetColumns).forEach((key) => {
    delete relationConfigTargetColumns[key]
  })
}

function handleRelationConfigSourceChange() {
  relationConfigSourceColumn.value = pickDefaultRelationConfigSourceColumn(relationConfigSource.value)
  relationConfigTargets.value = []
  Object.keys(relationConfigTargetColumns).forEach((key) => {
    delete relationConfigTargetColumns[key]
  })
}

function handleRelationConfigSourceColumnChange() {
  relationConfigTargets.value = relationConfigTargets.value.filter((target) => !isRelationTargetDisabled(target))
  handleRelationConfigTargetsChange()
}

function handleRelationConfigTargetsChange() {
  Object.keys(relationConfigTargetColumns).forEach((target) => {
    if (!relationConfigTargets.value.includes(target)) {
      delete relationConfigTargetColumns[target]
    }
  })
  relationConfigTargets.value.forEach((target) => {
    relationConfigTargetColumns[target] = relationConfigTargetColumns[target] || pickDefaultRelationConfigTargetColumn(target)
  })
}

function handleGraphPickerClick(tableName: string) {
  if (!relationConfigSource.value || tableName === relationConfigSource.value) {
    relationConfigSource.value = tableName
    handleRelationConfigSourceChange()
    return
  }
  if (isRelationTargetDisabled(tableName)) {
    ElMessage.warning('该表没有与起点字段相同的字段，不能作为连线表')
    return
  }
  if (editingRelationLabel.value) {
    relationConfigTargets.value = [tableName]
  } else if (relationConfigTargets.value.includes(tableName)) {
    relationConfigTargets.value = relationConfigTargets.value.filter((target) => target !== tableName)
  } else {
    relationConfigTargets.value = [...relationConfigTargets.value, tableName]
  }
  handleRelationConfigTargetsChange()
}

function submitRelationConfig() {
  if (!canSubmitRelationConfig.value) {
    return
  }
  if (editingRelationLabel.value) {
    removeModelEdge(editingRelationLabel.value)
  }
  relationConfigTargets.value.forEach((target) => {
    addManualRelation(
      relationConfigSource.value,
      relationConfigSourceColumn.value,
      target,
      relationConfigTargetColumns[target],
      'ONE_TO_MANY'
    )
  })
  relationSourceTable.value = ''
  relationDialogVisible.value = false
}

function deleteEditingRelation() {
  if (!editingRelationLabel.value) {
    return
  }
  removeModelEdge(editingRelationLabel.value)
  ElMessage.success('关系已删除')
  relationDialogVisible.value = false
}

function targetOptionLabel(tableName: string) {
  if (!relationConfigSource.value || tableName === relationConfigSource.value) {
    return tableName
  }
  return isRelationTargetDisabled(tableName) ? `${tableName}（无相同字段）` : tableName
}

function targetColumnOptions(tableName: string) {
  const columns = getModelNodeColumns(tableName)
  if (editingRelationLabel.value) {
    return columns
  }
  const sourceKey = relationFieldKey(relationConfigSourceColumn.value)
  return columns.filter((column) => relationFieldKey(column.name) === sourceKey)
}

function isRelationTargetDisabled(tableName: string) {
  if (!relationConfigSource.value || tableName === relationConfigSource.value) {
    return true
  }
  if (editingRelationLabel.value) {
    return false
  }
  return !targetColumnOptions(tableName).length
}

function pickDefaultRelationConfigSourceColumn(tableName: string) {
  const columns = getModelNodeColumns(tableName)
  const compatible = columns.find((column) => hasCompatibleTargetField(tableName, column.name))
  return compatible?.name || ''
}

function pickDefaultRelationConfigTargetColumn(tableName: string) {
  const columns = targetColumnOptions(tableName)
  return columns[0]?.name || ''
}

function hasCompatibleTargetField(sourceTable: string, sourceColumn: string) {
  const sourceKey = relationFieldKey(sourceColumn)
  return modelCanvasNodes.value.some((node) => {
    return node.tableName !== sourceTable && node.columns.some((column) => relationFieldKey(column.name) === sourceKey)
  })
}

function relationFieldKey(value: string) {
  return value.trim().toLowerCase()
}

function dialogPreviewPath(target: string) {
  const targets = relationConfigTargets.value
  const index = Math.max(0, targets.indexOf(target))
  const gap = targets.length > 1 ? 48 : 0
  const offset = (index - (targets.length - 1) / 2) * gap
  const startX = 82
  const startY = dialogPreviewSize.height / 2
  const endX = 232
  const endY = dialogPreviewSize.height / 2 + offset
  return `M ${startX} ${startY} C ${startX + 52} ${startY}, ${endX - 52} ${endY}, ${endX} ${endY}`
}

function handleManualSourceChange() {
  manualRelationSourceColumn.value = pickDefaultSourceColumn(manualRelationSource.value)
  if (manualRelationSource.value === manualRelationTarget.value) {
    manualRelationTarget.value = ''
    manualRelationTargetColumn.value = ''
  }
}

function handleManualTargetChange() {
  manualRelationTargetColumn.value = pickDefaultTargetColumn(manualRelationTarget.value, manualRelationSourceColumn.value)
}

function resetManualRelationForm() {
  manualRelationSource.value = ''
  manualRelationSourceColumn.value = ''
  manualRelationTarget.value = ''
  manualRelationTargetColumn.value = ''
}

function getModelNodeColumns(tableName: string) {
  return modelCanvasNodes.value.find((node) => node.tableName === tableName)?.columns || []
}

function pickDefaultSourceColumn(tableName: string) {
  const columns = getModelNodeColumns(tableName)
  return columns.find((column) => column.badge === 'PK')?.name || columns[0]?.name || ''
}

function pickDefaultTargetColumn(tableName: string, sourceColumn = '') {
  const columns = getModelNodeColumns(tableName)
  if (!columns.length) {
    return ''
  }
  const exact = columns.find((column) => column.name === sourceColumn)
  const foreignKey = columns.find((column) => column.badge === 'FK')
  const primaryKey = columns.find((column) => column.badge === 'PK')
  return exact?.name || foreignKey?.name || primaryKey?.name || columns[0].name
}

function fieldOptionLabel(column: TableNode['columns'][number]) {
  return column.badge ? `${column.name} (${column.badge})` : column.name
}

function visibleNodeColumns(node: TableNode) {
  return [...node.columns]
    .filter((column) => isRelatedNodeColumn(node.tableName, column.name))
    .sort((left, right) => {
      const leftKey = columnPriority(left)
      const rightKey = columnPriority(right)
      if (leftKey !== rightKey) {
        return leftKey - rightKey
      }
      return node.columns.findIndex((column) => column.name === left.name) - node.columns.findIndex((column) => column.name === right.name)
    })
    .slice(0, 5)
}

function isRelatedNodeColumn(tableName: string, columnName: string) {
  return currentEdges().some((edge) => {
    return (edge.source === tableName && edge.sourceColumn === columnName)
      || (edge.target === tableName && edge.targetColumn === columnName)
  })
}

function currentEdges() {
  return activeModule.value === '关系模型' ? modelCanvasEdges.value : overview.value?.edges || []
}

function columnPriority(column: TableNode['columns'][number]) {
  if (column.badge === 'PK') {
    return 1
  }
  if (column.badge === 'FK') {
    return 2
  }
  return 3
}

function selectModule(title: string) {
  const group = menuGroups.find((item) => item.title === title)
  if (group) {
    activeSubMenu.value = group.items[0] || group.title
  }
}

function selectMenu(_title: string, item: string) {
  activeSubMenu.value = item
}

function dataSourceStatusType(status: DataSourceItem['status']) {
  if (status === 'CONNECTED') {
    return 'success'
  }
  if (status === 'FAILED') {
    return 'danger'
  }
  return 'info'
}

function formatTime(value?: string) {
  if (!value) {
    return '-'
  }
  return new Date(value).toLocaleString()
}

function formatCellValue(value: unknown) {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  if (typeof value === 'object') {
    return JSON.stringify(value)
  }
  return String(value)
}

function renderMarkdown(value: string) {
  const lines = escapeHtml(value).split(/\n/)
  const html: string[] = []
  let inList = false
  lines.forEach((line) => {
    const trimmed = line.trim()
    if (!trimmed) {
      if (inList) {
        html.push('</ul>')
        inList = false
      }
      return
    }
    const heading = /^(#{1,3})\s+(.+)$/.exec(trimmed)
    if (heading) {
      if (inList) {
        html.push('</ul>')
        inList = false
      }
      html.push(`<h${heading[1].length}>${renderInlineMarkdown(heading[2])}</h${heading[1].length}>`)
      return
    }
    const listItem = /^[-*]\s+(.+)$/.exec(trimmed)
    if (listItem) {
      if (!inList) {
        html.push('<ul>')
        inList = true
      }
      html.push(`<li>${renderInlineMarkdown(listItem[1])}</li>`)
      return
    }
    if (inList) {
      html.push('</ul>')
      inList = false
    }
    html.push(`<p>${renderInlineMarkdown(trimmed)}</p>`)
  })
  if (inList) {
    html.push('</ul>')
  }
  return html.join('')
}

function renderInlineMarkdown(value: string) {
  return value
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function resetDataSourceForm() {
  dataSourceForm.name = ''
  dataSourceForm.databaseType = 'MySQL'
  dataSourceForm.jdbcUrl = ''
  dataSourceForm.username = ''
  dataSourceForm.password = ''
  dataSourceForm.description = ''
  operationMessage.value = ''
}

function startCreateDataSource() {
  resetDataSourceForm()
  editingDataSource.value = true
  editingDataSourceId.value = undefined
}

function startEditDataSource(item: DataSourceItem) {
  editingDataSource.value = true
  editingDataSourceId.value = item.id
  selectedDataSourceId.value = item.id
  dataSourceForm.name = item.name
  dataSourceForm.databaseType = item.databaseType
  dataSourceForm.jdbcUrl = item.jdbcUrl
  dataSourceForm.username = item.username
  dataSourceForm.password = ''
  dataSourceForm.description = item.description || ''
  operationMessage.value = ''
}

function cancelEditDataSource() {
  editingDataSource.value = false
  editingDataSourceId.value = undefined
  resetDataSourceForm()
}

async function loadDataSources() {
  dataSources.value = await fetchDataSources()
  if (!selectedDataSourceId.value && dataSources.value.length) {
    selectedDataSourceId.value = dataSources.value[0].id
  }
}

async function loadRelationModelPage() {
  relationModelLoading.value = true
  try {
    const page = await fetchRelationModels({
      keyword: relationModelKeyword.value.trim(),
      page: relationModelPageNo.value,
      size: relationModelPageSize.value
    })
    relationModelPage.value = normalizeRelationModelPage(page)
    if (!relationModelPage.value.models.length && relationModelPageNo.value > 1) {
      relationModelPageNo.value -= 1
      await loadRelationModelPage()
    }
  } finally {
    relationModelLoading.value = false
  }
}

async function loadQueryRelationModels() {
  const page = await fetchRelationModels({ keyword: '', page: 1, size: 100 })
  queryRelationModels.value = page.models
  if (!queryRelationModelId.value && queryRelationModels.value.length) {
    queryRelationModelId.value = queryRelationModels.value[0].id
    await handleQueryModelChange()
  }
  if (!aiAskRelationModelId.value && queryRelationModels.value.length) {
    aiAskRelationModelId.value = queryRelationModels.value[0].id
    await handleAiAskModelChange()
  }
}

async function handleQueryModelChange() {
  relationQueryResult.value = undefined
  relationQueryActivePanels.value = []
  queryTableName.value = ''
  queryColumnName.value = ''
  if (!queryRelationModelId.value) {
    queryModelDetail.value = undefined
    return
  }
  queryModelDetail.value = await fetchRelationModelDetail(queryRelationModelId.value)
  queryTableName.value = queryModelDetail.value.nodes[0]?.tableName || ''
  handleQueryTableChange()
}

function handleQueryTableChange() {
  relationQueryResult.value = undefined
  relationQueryActivePanels.value = []
  queryColumnName.value = queryColumnOptions.value.find((column) => column.badge === 'PK')?.name || queryColumnOptions.value[0]?.name || ''
}

async function handleAiAskModelChange() {
  aiAskSummaryAbortController.value?.abort()
  if (!aiAskRelationModelId.value) {
    aiAskModelDetail.value = undefined
    aiAskModelSummary.value = ''
    aiAskRules.value = []
    return
  }
  aiAskModelDetail.value = await fetchRelationModelDetail(aiAskRelationModelId.value)
  aiAskRules.value = await fetchModelPrompts(aiAskRelationModelId.value)
  const summary = await fetchAiQueryModelSummary(aiAskRelationModelId.value)
  aiAskModelSummary.value = summary.summary || buildAiAskModelSummary()
}

function buildAiAskModelSummary() {
  if (!aiAskModelEntities.value.length) {
    return '暂无可用模型实体。可以先在关系模型中创建模型、拖入表资源并配置关系，AI 问数会基于这些实体关系理解用户问题。'
  }
  const relationCount = aiAskModelRelations.value.length
  const centralEntity = findCentralAiAskEntity()
  const connectedEntities = aiAskModelRelations.value
    .filter((edge) => edge.source === centralEntity || edge.target === centralEntity)
    .map((edge) => edge.source === centralEntity ? edge.target : edge.source)
    .filter((name, index, list) => list.indexOf(name) === index)
    .slice(0, 4)
  if (!relationCount) {
    return `当前模型包含 ${aiAskModelEntities.value.length} 个实体，AI 可以根据用户问题识别表和字段，并生成单实体查询计划；配置实体关系后，可继续扩展为跨表关联问数。`
  }
  const connectedText = connectedEntities.length ? `，并关联 ${connectedEntities.join('、')} 等数据` : ''
  return `当前模型以 ${centralEntity} 等业务实体为基础，已沉淀 ${relationCount} 条实体关系。AI 会先识别用户问题中的业务对象，再沿模型关系规划可查询路径${connectedText}，最终交给数据查询工具执行。`
}

function buildEntityCapability(node: TableNode) {
  const pk = node.columns.find((column) => column.badge === 'PK')?.name
  const fkCount = node.columns.filter((column) => column.badge === 'FK').length
  if (pk && fkCount) {
    return `可按 ${pk} 定位主记录，并沿 ${fkCount} 个外键字段查询上下游数据。`
  }
  if (pk) {
    return `可按 ${pk} 精确查询，也可作为其他实体的关联入口。`
  }
  return '可参与关联查询，适合按业务字段进行筛选和补充展示。'
}

function findCentralAiAskEntity() {
  const scores = new Map(aiAskModelEntities.value.map((entity) => [entity.name, 0]))
  aiAskModelRelations.value.forEach((edge) => {
    scores.set(edge.source, (scores.get(edge.source) || 0) + 1)
    scores.set(edge.target, (scores.get(edge.target) || 0) + 1)
  })
  return [...scores.entries()].sort((left, right) => right[1] - left[1])[0]?.[0] || aiAskModelEntities.value[0]?.name || '核心实体'
}

async function generateAiAskModelSummary() {
  if (!aiAskModelDetail.value) {
    ElMessage.warning('请先选择关系模型')
    return
  }
  aiAskSummaryAbortController.value?.abort()
  aiAskSummaryAbortController.value = new AbortController()
  aiAskSummaryGenerating.value = true
  const currentSummary = aiAskModelSummary.value
  aiAskModelSummary.value = ''
  try {
    await summarizeAiQueryModelStream(
      aiAskRelationModelId.value,
      currentSummary,
      {
        onDelta: (delta) => {
          aiAskModelSummary.value += delta
        },
        onComplete: (result) => {
          aiAskModelSummary.value = result.summary || aiAskModelSummary.value || buildAiAskModelSummary()
        },
        onError: (message) => ElMessage.error(message)
      },
      aiAskSummaryAbortController.value.signal
    )
    ElMessage.success('已生成模型解读说明')
  } catch (error) {
    if ((error as Error).name !== 'AbortError') {
      aiAskModelSummary.value = currentSummary || buildAiAskModelSummary()
      ElMessage.error((error as Error).message || '模型解读失败')
    }
  } finally {
    aiAskSummaryGenerating.value = false
    aiAskSummaryAbortController.value = undefined
  }
}

async function submitAiAskRule() {
  if (editingAiAskRuleId.value) {
    await updateAiAskRule()
  } else {
    await addAiAskRule()
  }
}

async function addAiAskRule() {
  const content = aiAskRuleDraft.value.trim()
  if (!content) {
    return
  }
  if (!aiAskRelationModelId.value) {
    ElMessage.warning('请先选择关系模型')
    return
  }
  if (aiAskRules.value.some((rule) => rule.content === content)) {
    ElMessage.warning('提示词已存在')
    return
  }
  const saved = await createModelPrompt(aiAskRelationModelId.value, content)
  aiAskRules.value.push(saved)
  aiAskRuleDraft.value = ''
}

async function updateAiAskRule() {
  const content = aiAskRuleDraft.value.trim()
  if (!content || !aiAskRelationModelId.value || !editingAiAskRuleId.value) {
    return
  }
  const saved = await updateModelPrompt(aiAskRelationModelId.value, editingAiAskRuleId.value, content)
  aiAskRules.value = aiAskRules.value.map((rule) => rule.id === saved.id ? saved : rule)
  aiAskRuleDraft.value = ''
  editingAiAskRuleId.value = ''
}

function startEditAiAskRule(rule: ModelPrompt) {
  editingAiAskRuleId.value = rule.id
  aiAskRuleDraft.value = rule.content
}

function cancelEditAiAskRule() {
  editingAiAskRuleId.value = ''
  aiAskRuleDraft.value = ''
}

async function removeAiAskRule(id: string) {
  if (!aiAskRelationModelId.value) {
    return
  }
  await deleteModelPrompt(aiAskRelationModelId.value, id)
  aiAskRules.value = aiAskRules.value.filter((rule) => rule.id !== id)
  if (editingAiAskRuleId.value === id) {
    cancelEditAiAskRule()
  }
}

async function resetAiAskRules() {
  if (!aiAskRelationModelId.value) {
    ElMessage.warning('请先选择关系模型')
    return
  }
  aiAskRules.value = await resetModelPrompts(aiAskRelationModelId.value)
  aiAskRuleDraft.value = ''
  editingAiAskRuleId.value = ''
}

async function sendAiAskQuestion() {
  const question = aiAskQuestion.value.trim()
  if (!question) {
    return
  }
  if (!aiAskRelationModelId.value) {
    ElMessage.warning('请先选择关系模型')
    return
  }
  const userMessage: AiAskMessage = {
    id: Date.now(),
    role: 'user',
    content: question,
    entities: recognizeQuestionEntities(question)
  }
  aiAskMessages.value.push(userMessage)
  aiAskQuestion.value = ''
  aiAskLoading.value = true
  aiAskChatAbortController.value?.abort()
  aiAskChatAbortController.value = new AbortController()
  const assistantMessageId = Date.now() + 1
  const assistantMessage: AiAskMessage = {
    id: assistantMessageId,
    role: 'assistant',
    content: '',
    entities: [],
    toolSteps: [],
    result: {
      title: '查询执行结果',
      summary: `问题：${question}`,
      tables: [],
      sql: '',
      columns: [],
      rows: [],
      rowCount: 0
    }
  }
  aiAskMessages.value.push(assistantMessage)
  scrollAiChatToBottom()
  try {
    await chatWithAiQueryStream(
      {
        modelId: aiAskRelationModelId.value,
        question,
        limit: 100
      },
      {
        onStep: (step) => {
          updateAiAskMessage(assistantMessageId, (message) => {
            message.toolSteps = [...(message.toolSteps || []), step]
          })
        },
        onSql: (sql) => {
          updateAiAskMessage(assistantMessageId, (message) => {
            message.result = ensureAiAskResult(message, question)
            message.result.sql = sql
          })
        },
        onResult: (result) => applyAiQueryExecutionResult(assistantMessageId, question, result),
        onAnswerDelta: (delta) => {
          updateAiAskMessage(assistantMessageId, (message) => {
            message.content += delta
          })
        },
        onComplete: (result) => {
          updateAiAskMessage(assistantMessageId, (message) => {
            message.content = result.answer || message.content
            message.entities = result.entities
            if (result.sql || result.columns.length || result.rows.length) {
              message.result = ensureAiAskResult(message, question)
              message.result.sql = result.sql
              message.result.columns = result.columns
              message.result.rows = result.rows
              message.result.rowCount = result.rowCount
              message.result.tables = [
                {
                  name: aiAskModelDetail.value?.name || '查询结果',
                  rows: result.rowCount,
                  fields: result.columns
                }
              ]
            } else {
              message.result = undefined
            }
          })
        },
        onError: (message) => {
          updateAiAskMessage(assistantMessageId, (item) => {
            item.content = message
          })
          ElMessage.error(message)
        }
      },
      aiAskChatAbortController.value.signal
    )
  } catch (error) {
    if ((error as Error).name !== 'AbortError') {
      const message = (error as Error).message || 'AI 问数失败'
      updateAiAskMessage(assistantMessageId, (item) => {
        item.content = message
      })
      ElMessage.error(message)
    }
  } finally {
    aiAskLoading.value = false
    aiAskChatAbortController.value = undefined
  }
}

function updateAiAskMessage(messageId: number, updater: (message: AiAskMessage) => void) {
  aiAskMessages.value = aiAskMessages.value.map((message) => {
    if (message.id !== messageId) {
      return message
    }
    const next: AiAskMessage = {
      ...message,
      entities: message.entities ? [...message.entities] : undefined,
      toolSteps: message.toolSteps ? message.toolSteps.map((step) => ({ ...step })) : undefined,
      result: message.result
        ? {
            ...message.result,
            tables: message.result.tables.map((table) => ({ ...table, fields: [...table.fields] })),
            columns: message.result.columns ? [...message.result.columns] : undefined,
            rows: message.result.rows ? [...message.result.rows] : undefined
          }
        : undefined
    }
    updater(next)
    return next
  })
  scrollAiChatToBottom()
}

function ensureAiAskResult(message: AiAskMessage, question: string) {
  if (!message.result) {
    message.result = {
      title: '查询执行结果',
      summary: `问题：${question}`,
      tables: [],
      sql: '',
      columns: [],
      rows: [],
      rowCount: 0
    }
  }
  return message.result
}

function applyAiQueryExecutionResult(messageId: number, question: string, result: AiQueryChatExecutionResult) {
  updateAiAskMessage(messageId, (message) => {
    message.result = ensureAiAskResult(message, question)
    message.result.columns = result.columns
    message.result.rows = result.rows
    message.result.rowCount = result.rowCount
    message.result.tables = [
      {
        name: aiAskModelDetail.value?.name || '查询结果',
        rows: result.rowCount,
        fields: result.columns
      }
    ]
  })
}

function scrollAiChatToBottom() {
  nextTick(() => {
    const target = aiChatThreadRef.value
    if (target) {
      target.scrollTop = target.scrollHeight
    }
  })
}

function recognizeQuestionEntities(question: string) {
  const normalizedQuestion = question.toLowerCase()
  const matched = aiAskModelEntities.value
    .filter((entity) => {
      return normalizedQuestion.includes(entity.name.toLowerCase())
        || normalizedQuestion.includes(entity.comment.toLowerCase())
        || entity.keyFields.some((field) => normalizedQuestion.includes(field.toLowerCase()))
    })
    .map((entity) => entity.name)
  if (matched.length) {
    return matched
  }
  return aiAskModelEntities.value.slice(0, 2).map((entity) => entity.name)
}

async function runRelationDataQuery() {
  if (!queryRelationModelId.value || !queryTableName.value || !queryColumnName.value || !queryColumnValue.value.trim()) {
    ElMessage.warning('请选择模型、表、字段并输入查询值')
    return
  }
  relationQueryLoading.value = true
  try {
    relationQueryResult.value = await queryAssociatedData({
      relationModelId: queryRelationModelId.value,
      mainTable: queryTableName.value,
      keyColumn: queryColumnName.value,
      keyValue: queryColumnValue.value.trim()
    })
    relationQueryActivePanels.value = relationQueryResult.value.tables.map((table) => table.tableName)
    if (!relationQueryResult.value.tables.length) {
      ElMessage.warning('没有查询到关联数据')
    }
  } finally {
    relationQueryLoading.value = false
  }
}

function normalizeRelationModelPage(page: RelationModelPage) {
  const models = (page.models || []).map((model) => ({
    ...model,
    tableNames: Array.isArray(model.tableNames) ? model.tableNames : []
  }))
  return {
    page: page.page || relationModelPageNo.value,
    size: page.size || relationModelPageSize.value,
    total: page.total || models.length,
    models
  }
}

function tableNamesOf(row: RelationModelListItem) {
  return Array.isArray(row.tableNames) ? row.tableNames : []
}

function handleRelationModelExpandChange(row: RelationModelListItem, expandedRows: RelationModelListItem[]) {
  relationModelExpandedKeys.value = expandedRows.some((item) => item.id === row.id) ? [row.id] : []
}

function searchRelationModels() {
  relationModelPageNo.value = 1
  loadRelationModelPage().catch(() => undefined)
}

async function loadMetadataTablePage() {
  if (!selectedDataSourceId.value) {
    metadataTablePage.value = undefined
    selectedMetadataTables.value = []
    return
  }
  metadataLoading.value = true
  try {
    selectedMetadataTables.value = []
    metadataTablePage.value = await fetchMetadataTables(selectedDataSourceId.value, {
      keyword: metadataKeyword.value.trim(),
      page: metadataPage.value,
      size: metadataPageSize.value
    })
    if (!metadataTablePage.value.tables.length && metadataPage.value > 1) {
      metadataPage.value -= 1
      await loadMetadataTablePage()
    }
  } finally {
    metadataLoading.value = false
  }
}

function searchMetadataTables() {
  metadataPage.value = 1
  loadMetadataTablePage().catch(() => undefined)
}

function handleMetadataSelectionChange(rows: MetadataTableItem[]) {
  selectedMetadataTables.value = rows
}

async function submitDataSource() {
  const wasEditing = Boolean(editingDataSourceId.value)
  const saved = editingDataSourceId.value
    ? await updateDataSource(editingDataSourceId.value, dataSourceForm)
    : await createDataSource(dataSourceForm)
  selectedDataSourceId.value = saved.id
  await loadDataSources()
  editingDataSource.value = false
  editingDataSourceId.value = undefined
  resetDataSourceForm()
  operationMessage.value = wasEditing ? `已更新数据源：${saved.name}` : `已保存数据源：${saved.name}`
}

async function runConnectionTest(id = selectedDataSourceId.value) {
  if (!id) {
    return
  }
  selectedDataSourceId.value = id
  const result = await testDataSourceConnection(id)
  if (result.success) {
    ElMessage.success(result.message)
  } else {
    ElMessage.error(result.message)
  }
  await loadDataSources()
}

async function removeDataSource(id: number) {
  await deleteDataSource(id)
  if (selectedDataSourceId.value === id) {
    selectedDataSourceId.value = undefined
    metadataTablePage.value = undefined
  }
  await loadDataSources()
  operationMessage.value = '已删除数据源'
}

async function runMetadataCollect() {
  if (!selectedDataSourceRecord.value) {
    return
  }
  try {
    metadataResult.value = await collectDataSourceMetadata(selectedDataSourceRecord.value.id)
    ElMessage.success(`元数据采集完成：${metadataResult.value.tableCount} 张表`)
    await loadDataSources()
    metadataPage.value = 1
    await loadMetadataTablePage()
  } catch {
    ElMessage.error('元数据采集失败，请检查数据源连接配置')
  }
}

async function removeMetadataTable(tableId: string, tableName: string) {
  if (!selectedDataSourceId.value) {
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除元数据表「${tableName}」？删除后该表字段也会一并移除。`, '删除表', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await deleteMetadataTable(selectedDataSourceId.value, tableId)
  ElMessage.success('元数据表已删除')
  await loadDataSources()
  await loadMetadataTablePage()
}

async function removeSelectedMetadataTables() {
  if (!selectedDataSourceId.value || !selectedMetadataTables.value.length) {
    return
  }
  const selectedCount = selectedMetadataTables.value.length
  try {
    await ElMessageBox.confirm(`确认删除选中的 ${selectedCount} 张元数据表？删除后这些表的字段也会一并移除。`, '批量删除表', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await deleteMetadataTables(
    selectedDataSourceId.value,
    selectedMetadataTables.value.map((table) => table.id)
  )
  ElMessage.success(`已删除 ${selectedCount} 张元数据表`)
  selectedMetadataTables.value = []
  await loadDataSources()
  await loadMetadataTablePage()
}

watch(selectedDataSourceId, () => {
  metadataPage.value = 1
  selectedMetadataTables.value = []
  loadMetadataTablePage().catch(() => undefined)
})

onMounted(async () => {
  document.addEventListener('fullscreenchange', syncCanvasFullscreenState)
  try {
    overview.value = await fetchWorkbenchOverview()
    localNodes.value = overview.value.nodes.map((node) => ({ ...node }))
    selectedDataSource.value = overview.value.selectedDataSource
    selectedModel.value = overview.value.selectedModel
  } catch {
    localNodes.value = []
  }
  try {
    await loadDataSources()
  } catch {
    dataSources.value = []
  }
  try {
    await loadRelationModelPage()
  } catch {
    relationModelPage.value = { page: 1, size: relationModelPageSize.value, total: 0, models: [] }
  }
  try {
    await loadQueryRelationModels()
  } catch {
    queryRelationModels.value = []
  }
  try {
    const modelResult = await fetchAiModels()
    aiModels.value = modelResult.models
    selectedAiModel.value = aiModels.value.find((model) => model.id === modelResult.defaultModel && model.enabled)?.id
      || aiModels.value.find((model) => model.enabled)?.id
      || ''
  } catch {
    aiModels.value = []
    selectedAiModel.value = ''
  }
  modelDraft.dataSourceId = dataSources.value[0]?.id
  await nextTick()
  fitCanvas()
})

onUnmounted(() => {
  aiAskSummaryAbortController.value?.abort()
  aiAskChatAbortController.value?.abort()
  document.removeEventListener('fullscreenchange', syncCanvasFullscreenState)
})
</script>
