<template>
  <!-- 保持原有template内容完全一致 -->
  <div class="chat-container">
    <!-- 顶部导航栏 -->
    <div class="top-navbar">
      <div class="navbar-content">
        <div class="navbar-logo">
          <img src="/assets/img/yj.png" alt="Logo" class="logo-image" />
          "易道生生"IWEN大模型
        </div>
        <!-- 用户信息 & 退出 -->
        <div class="navbar-user-info">
          <span class="nickname">{{ nickname }}</span>
          <button class="logout-button" @click="logout">退出登录</button>
        </div>
      </div>
    </div>

    <!-- 左侧功能栏 -->
    <div class="sidebar" :class="{ 'collapsed': isSidebarCollapsed }">
      <div class="sidebar-toggle" @click="toggleSidebar">
        <span v-if="isSidebarCollapsed">➤</span>
        <span v-else>◀ 收起</span>
      </div>
      <div v-if="!isSidebarCollapsed" class="sidebar-content">
        <ul>
          <li @click="startNewConversation">
            <i class="fas fa-history"></i> 新建对话
          </li>
          <ChatHistoryList @select="selectConversation" />
        </ul>
        <div class="feedback" @click="goToFeedback">
          问题反馈
        </div>
      </div>
    </div>

    <div class="background-container">
      <div class="chat-messages horizontal-layout"
           ref="chatMessagesRef">
        <div
            v-for="(message, index) in messages"
            :key="index"
            class="message"
            :class="[message.type === 'bot' ? message.layoutClass : '', message.type]"
        >
          <!-- 机器人消息 -->
          <template v-if="message.type === 'bot'">
            <div class="rating-avatar-container">
              <div class="avatar-container">
                <img :src="message.avatar" :alt="message.type" class="avatar" />
              </div>
              <div class="rating">
                <span class="rating-label">回答评分：</span>
                <span v-if="!message.rating">
        <span
            v-for="n in 5"
            :key="n"
            class="star"
            :class="{ 'filled': message.hoverRating !== null && n <= message.hoverRating }"
            @click="rateMessage(message, n, message.model)"
            @mouseover="message.hoverRating = n"
            @mouseleave="message.hoverRating = null"
        >★</span>
      </span>
                <span v-else class="stars-with-rating">
        <span v-for="n in 5" :key="n" class="star" :class="{'filled': n <= message.rating}">★</span>
        <span class="rating-score">{{ message.rating }}</span>
      </span>
              </div>
            </div>

            <!-- 机器人文本内容，只显示非卦象请求 -->
            <div
                class="bot-message"
                v-if="message.text.length > 0 && !message.d3Code"
            >
              <template v-if="message.model === 'local' || message.model === 'local32B' || message.model === 'local32BnoRAG'">
    <span v-for="(chunk, index) in message.text" :key="index" class="response-span">
      {{ chunk }}
    </span>
              </template>
              <span v-else v-html="message.text"></span>
              <!--              <div class="timestamp">{{ formatTimestamp(message.timestamp) }}</div>-->
            </div>
            <!-- 播放语音按钮 -->
            <div class="voice-button-container"
                 v-if="message.text && (typeof message.text === 'string' || message.text.length) && !message.d3Code">
              <button @click="speakText(message)" class="voice-button">
                <span v-if="!message.isSpeaking">📢 听语音</span>
                <span v-else-if="message.isPaused">▶️ 继续</span>
                <span v-else>⏸ 暂停</span>
              </button>

              <button
                  @click="stopSpeech(message)"
                  class="voice-button"
                  v-if="message.isSpeaking"
                  style="margin-left: 8px;">
                ⏹ 停止
              </button>
            </div>




            <!-- D3 图形容器 -->
            <div v-if="message.d3Code">
              <div :id="'diagram-' + message.id" class="d3-container" style="margin-top: 12px;"></div>
            </div>
          </template>


          <!-- 用户消息 -->
          <template v-else>
            <div class="user-message-container">
              <div class="user-message">
                <span v-html="message.text"></span>
                <!--                <div class="timestamp">{{ formatTimestamp(message.timestamp) }}</div>-->
              </div>
              <div class="avatar-container">
                <img :src="message.avatar" :alt="message.type" class="avatar" />
              </div>
            </div>
          </template>

        </div>
        <div v-if="isTyping" class="typing-indicator">正在思考，请稍等...</div>
        <!--        <div ref="chartRef" class="chart-box" v-show="chartVisible"></div>-->
      </div>


      <div v-if="showModelTip" class="model-tip">
        <i class="fas fa-info-circle"></i> 请选择你需要的大模型（最多可选三个模型）<br>你可以对不同大模型的回答进行打分
      </div>
      <div class="graph-button-container">
        <el-button
            @click="showGraphData"
            class="load-btn"
            :disabled="buttonDisabled">
          显示知识图谱
        </el-button>

        <el-dialog
            v-model="chartVisible"
            title="知识图谱"
            width="70%"
            top="5vh"
            :destroy-on-close="false"
            @opened="onDialogOpened"
            @closed="onDialogClosed"
        >
          <div ref="chartRef" class="chart-box" style="height:600px;"></div>
        </el-dialog>
      </div>
      <div class="model-selector-container">
        <button
            class="model-select-button"
            :class="{ 'selected-model': selectedModels.includes('local') }"
            @click="toggleModelMenu"
        >
          <i class="fas fa-robot"></i> 选择模型 ▼
        </button>
        <div v-show="isModelMenuOpen" class="model-menu">
          <div
              v-for="model in modelOptions"
              :key="model.value"
              class="model-menu-item"
              :class="{'selected': selectedModels.includes(model.value), 'disabled': !selectedModels.includes(model.value) && selectedModels.length >= 3}"

              @click="selectModel(model.value)"
          >
            {{ model.label }}
          </div>
        </div>
      </div>

      <div class="chat-input-container">
        <div class="input-box">
          <input v-model="userInput" type="text" @keyup.enter="sendMessageAll" placeholder="请输入您要问的问题"/>
        </div>
        <button @click="sendMessageAll" class="send-button" :disabled="isSendingMessage">发送</button>
        <button class="send-button" :disabled="isSendingMessage" @click="upload">返回首页</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import request from "../utils/request"
import { reactive, nextTick } from 'vue'
import ChatHistoryList from "@/views/ChatHistoryList.vue";

const router = useRouter()

// 静态资源引入
import userAvatar from '../../public/assets/img/touxiang.jpg'
import botAvatar from '../../public/assets/img/yitan.png'
import bot32BAvatar from '../../public/assets/img/bagua.png'
import bot32BAvatarnoRAG from '../../public/assets/img/upup.png'
import deepseekAvatar from '../../public/assets/img/touxinag_deepseek.png'
import kimiAvatar from '../../public/assets/img/touxiang_kimi.png'
import eventBus from '@/utils/eventBus'
import * as echarts from "echarts";

// 响应式状态
const apiKey = ref("sk-aWheLOkwpDLcjDsmT05PNriib45SgFohvJUvUNk0wWZuwAyR")
const apiKey_DeepSeek = ref("sk-a0bc3d1bb1cd4a88aceb5a7bbcea243a")

const nickname = ref(localStorage.getItem('nickname') || '默认昵称')
const isNearBottom = ref(true)
const userInput = ref('')
const messages = ref([])
const isTyping = ref(false)
const isSidebarCollapsed = ref(false)
const selectedModels = ref(['local'])
const isModelMenuOpen = ref(false)
const showModelTip = ref(true)
const isSendingMessage = ref(false)
const responseData = ref([])
const chatMessagesRef = ref(null)
const graphDataMap = ref(new Map()) // 存储每个问题的图谱数据
const currentQuestion = ref('')     // 当前问题标识
const buttonDisabled = ref(true); // 初始禁用
let UserQues = ref('')
let modelId;
let question =ref('')
let currentConversationId = null

const chartRef = ref(null); // 绑定 DOM 容器
let chartInstance = null
let chartVisible = ref(false); // 控制显示

//语音播报
const isSpeaking = ref(false)
const isPausedRef = ref(false)

let utterance = null
let currentUtterance = null
let currentSpeakingMessageId = null

watch(
    () => messages.value,
    () => {
      nextTick(() => {
        checkScrollPosition() // 先检查当前位置
        scrollToBottom()     // 根据位置决定是否滚动
      })
    },
    { deep: true }
)

const checkScrollPosition = () => {
  if (!chatMessagesRef.value) return

  const { scrollTop, scrollHeight, clientHeight } = chatMessagesRef.value
  const distanceFromBottom = scrollHeight - (scrollTop + clientHeight)
  isNearBottom.value = distanceFromBottom < 100  // 距离底部小于100px视为在底部区域
}
// 3. 滚动方法
const scrollToBottom = () => {
  if (isNearBottom.value && chatMessagesRef.value) {
    chatMessagesRef.value.scrollTop = chatMessagesRef.value.scrollHeight
  }
}

onMounted(() => {
  if (chatMessagesRef.value) {
    chatMessagesRef.value.addEventListener('scroll', checkScrollPosition)
  }
})

onBeforeUnmount(() => {
  if (chatMessagesRef.value) {
    chatMessagesRef.value.removeEventListener('scroll', checkScrollPosition)
  }
})


const modelOptions = ref([
  {label: 'IWEN(14B)', value: 'local'},
  {label: 'IWEN(32B)', value: 'local32B'},
  {label: 'IWEN(32B)noRAG', value: 'local32BnoRAG'},
  {label: 'DeepSeek', value: 'deepseek'},
  {label: 'Kimi', value: 'kimi'}
])

const upload = () => {
  router.push('/homeView')
}


const goToFeedback = () => {
  router.push('/feedback')
}

const rateMessage = (message, rating, modelName) => {
  message.rating = rating
  const scoreData = {}

  // 只更新对应模型的分数
  switch (modelName) {
    case 'local': scoreData.score1 = rating; break
    case 'local32B': scoreData.score4 = rating; break
    case 'local32BnoRAG': scoreData.score5 = rating; break
    case 'kimi': scoreData.score2 = rating; break
    case 'deepseek': scoreData.score3 = rating; break
  }

  const dataToSend = {
    id: modelId,
    userMessage: question,
    selectedModels: selectedModels.value,
    ...scoreData
  }

  sendScoreToBackend(dataToSend)
}


const startNewConversation = () => {
  currentConversationId = null
  messages.value = []
  eventBus.emit('refresh-history')
}


const sendMessageAll = async () => {
  if (!userInput.value.trim()) return

  // 更新当前问题并保留旧问题的数据
  const newQuestion = userInput.value.trim()

  // 如果是新问题则清除当前问题的图谱缓存
  if (newQuestion !== currentQuestion.value) {
    currentQuestion.value = newQuestion
    graphDataMap.value.delete(currentQuestion.value)
  }

  await fetchGraphData()

  //console.log(localStorage.getItem('userId'))
  question = newQuestion
  userInput.value = ''

  messages.value.push({
    text: question,
    type: 'user',
    avatar: userAvatar,
    // timestamp: new Date()
  })

  const count = selectedModels.value.length
  const layoutClass = count === 2 ? 'two-models'
      : count >= 3 ? 'three-models'
          : 'one-model'

  const prompt = generatePrompt(question)
  //const prompt = question
  console.log("conID",currentConversationId)
  try {
    isSendingMessage.value = true
    isTyping.value = true
    showModelTip.value = false

    console.log("conID2",currentConversationId)

    if (!currentConversationId) {
      const newConvId = await createNewConversation({
        userid:localStorage.getItem('userId'),
        discribe: question
      })
      currentConversationId = newConvId
      console.log(currentConversationId,'*****')
    }

    const handlers = selectedModels.value.map(model =>
        createMessageHandler(model, prompt, layoutClass)
    )

    const responses = await Promise.all(handlers.map(fn => fn()))
    console.log(responses)
    console.log(selectedModels.value)

    const modelResponses = selectedModels.value.map((model, index) => {
      const response = responses[index]
      return {
        modelName: model,
        botResponse: response.text,
        ...(response.d3Code ? { d3Code: response.d3Code } : {})
      }
    })

    //聚合后发送给后端
    const backendData = {
      conversation: currentConversationId,
      selectedModels: [...selectedModels.value],
      userMessage: question,
      modelResponses: modelResponses
    }

    const responseData=await sendMessageToBackend(backendData)
    if (responseData) {
      modelId = responseData;
      console.log('modelId:',modelId);
    } else {
      console.error('后端返回的数据没有包含 id');
    }
  } catch (e) {
    console.error('全局错误:', e)
  } finally {
    isSendingMessage.value = false
    isTyping.value = false
  }

}

const createMessageHandler = (modelType, prompt, layoutClass) => {
  return async () => {
    const avatar = updateAvatar(modelType)
    let response

    const isGuaRequest = /(错卦|综卦|变卦|卦象|爻)/.test(prompt)
    //const isGuaRequest = /(nothing)/.test(prompt)

    const botMessage = reactive({
      id: Date.now() + Math.random(),
      model: modelType,
      text: '正在思考，请稍后...',
      avatar: avatar,
      type: 'bot',
      layoutClass: layoutClass,
      // timestamp: new Date(),
      rating: null,
      hoverRating: null,
      isSpeaking: false,  //
      isPaused: false
    })
    messages.value.push(botMessage)


    try {
      // 请求接口
      response = await sendRequestByModel(modelType, prompt)
      if (!response.ok || !response.body) throw new Error("响应失败或未接收到流数据")

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let fullText = ''
      botMessage.text = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const chunks = buffer.split('\n')
        buffer = chunks.pop() || ''

        for (const chunk of chunks) {
          const trimmed = chunk.trim()
          if (!trimmed) continue

          let content = extractStreamContent(trimmed, modelType)
          if (content) {
            if (isGuaRequest) {
              fullText += content

            } else {
              botMessage.text += content
              await nextTick()

            }
          }
        }
      }

      //
      if (isGuaRequest) {
        const d3Code = await renderD3SVG(botMessage, fullText)
        console.log("提取 d3code：", d3Code)
        return {
          text: fullText,
          ...(d3Code ? { d3Code } : {})
        }
      }else {
        return {
          text: botMessage.text
        }
      }


    } catch (err) {
      console.error(`${modelType}请求失败:`, err)
      const idx = messages.value.findIndex(m => m.id === botMessage.id)
      if (idx !== -1) messages.value[idx].text = `请求失败: ${err.message}`
    }
  }
}

const sendRequestByModel = async (modelType, prompt) => {
  switch (modelType) {
    case 'local':
      return await fetch("/qwen14b", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({question: prompt})
      })
    case 'local32B':
      return await fetch("/qwen32b", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({question: prompt})
      })
    case 'local32BnoRAG':
      return await fetch("/qwen32b_SFT", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({question: prompt})
      })
    case 'deepseek':
      return await fetch("https://api.deepseek.com/v1/chat/completions", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${apiKey_DeepSeek.value}`
        },
        body: JSON.stringify({
          model: "deepseek-chat",
          messages: [{role: "user", content: prompt}],
          temperature: 0.3,
          stream: true
        })
      })
    case 'kimi':
      return await fetch("https://api.moonshot.cn/v1/chat/completions", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${apiKey.value}`
        },
        body: JSON.stringify({
          model: "moonshot-v1-8k",
          messages: [{role: "user", content: prompt}],
          temperature: 0.3,
          stream: true
        })
      })
    default:
      throw new Error("未知模型")
  }
}

const extractStreamContent = (chunk, modelType) => {
  if (!chunk.startsWith('data: ')) return ''
  const payload = chunk.replace('data: ', '')

  if (payload === '[DONE]') return ''

  if (modelType === 'local' || modelType === 'local32B' || modelType === 'local32BnoRAG') {
    return payload.replace(/\\n/g, '\n')
  } else {
    const data = JSON.parse(payload)
    return data.choices?.[0]?.delta?.content || ''
  }
}


const generatePrompt = (question) => {
  const isGuaRequest = /(错卦|综卦|变卦|卦象|爻)/.test(question)
  //const isGuaRequest = /(nothing)/.test(question)
  if (!isGuaRequest) return question

  return `请严格按照用户的请求，绘制指定卦象的SVG+D3.js代码：
1. 根据用户输入的卦象绘制图形，例如“比卦的错卦”即比卦的每一个阴爻变成阳爻，阳爻变成阴爻图像
2. 阳爻（长线）颜色 #000，阴爻（短线）颜色 #888
3. 爻尺寸80x20px，垂直间距30px
4. SVG 容器 500x400
5. 仅使用 D3.js 生成 SVG 代码
6. 代码必须用<D3JS>包裹，禁止使用body选择器
7. 必须为每个爻图形元素添加stroke属性：
   - 阳爻 stroke="#000"
   - 阴爻 stroke="#888"
8. 使用<rect>元素绘制爻，尺寸严格80x20px
9. 所有爻必须直接位于svg根节点下，不要用<g>包裹`
}

const renderD3SVG = async (botMessage, fullText) => {
  const d3CodeRaw = extractD3Code(fullText)
  console.log("流式拼接结果：", fullText)
  console.log("提取的 d3CodeRaw：", d3CodeRaw)

  if (!d3CodeRaw) return

  botMessage.d3Code = d3CodeRaw
  await nextTick()

  const container = document.getElementById(`diagram-${botMessage.id}`)
  console.log("", container)
  if (!container) return

  container.innerHTML = ''
  const d3Code = d3CodeRaw
      .replace(/document\.body/g, 'container')
      .replace(/d3\.select\(["']#?\w+["']\)/g, 'd3.select(container)')
      .replace(/\.append\(["']svg["']\)/g, '.append("svg")')

  console.log("渲染代码：", d3Code)
  renderDiagram(d3Code, container)

  return d3CodeRaw
}

const extractD3Code = (text) => {
  const match = text.match(/<D3JS>([\s\S]*?)<\/D3JS>/)
  if (match) return match[1].trim()
  const start = text.indexOf('<D3JS>')
  const end = text.indexOf('</D3JS>')
  if (start !== -1 && end !== -1 && end > start) {
    return text.slice(start + 6, end).trim()
  }
  return null
}


function renderDiagram(code, container) {
  if (!window.d3) {
    console.error("D3.js 未加载，")
    return
  }

  if (!container) {
    console.error("找不到 SVG 容器")
    return
  }

  try {
// 清空旧图像
    container.innerHTML = ''

// 使用 new Function 包装并执行传入的 D3.js 代码
    const fn = new Function('d3', 'container', code)
    fn(d3, container)

    console.log("SVG 图形渲染成功")
  } catch (e) {
    console.error("图形渲染失败:", e)
    const errorMsg = document.createElement('div')
    errorMsg.textContent = "图形渲染失败，请检查 D3.js 代码格式。"
    errorMsg.style.color = 'red'
    container.appendChild(errorMsg)
  }
}

const createNewConversation = (data) => {
  console.log(data);  // 打印数据，确保格式正确
  return request.post('/conversation/addConversation', data)
      .then(response => {
        console.log('创建对话成功:', response);
        console.log(response.data.id); // 打印成功的响应
        return response.data.id;  // 返回数据
      })
      .catch(error => {
        console.error('创建对话失败:', error);
        throw error;  // 如果失败，抛出错误
      });
}

const sendMessageToBackend = (data) => {
  console.log(data);  // 打印数据，确保格式正确
  return request.post('/chatqa/addChatqa', data)
      .then(response => {
        console.log('消息发送成功:', response);  // 打印成功的响应
        return response.data;  // 返回数据
      })
      .catch(error => {
        console.error('发送消息到后端失败:', error);
        throw error;  // 如果失败，抛出错误
      });
}

const sendScoreToBackend = async (data) => {
  try {
    console.log('发送的数据:', JSON.stringify(data));
    console.log('发送的数据:',data);
    const response = await request.put('/chatqa/updateScores', data, {
      headers: {'Content-Type': 'application/json'}
    });
    console.log('评分数据发送成功:', response.data); // 打印返回的数据
  } catch (error) {
    console.error('发送评分数据到后端失败:', error);
  }
}


const toggleModelMenu = () => {
  if (!isModelMenuOpen.value) {
    isModelMenuOpen.value = !isModelMenuOpen.value
  }
}

const selectModel = (value) => {
  const index = selectedModels.value.indexOf(value)
  if (index === -1) {
    selectedModels.value.push(value)
  } else {
    selectedModels.value.splice(index, 1)
  }
}

const updateAvatar = (model) => {
  switch (model) {
    case 'deepseek':
      return deepseekAvatar
    case 'kimi':
      return kimiAvatar
    case 'local32B':
      return bot32BAvatar
    case 'local32BnoRAG':
      return bot32BAvatarnoRAG
    default:
      return botAvatar
  }
}



const toggleSidebar = () => {
  isSidebarCollapsed.value = !isSidebarCollapsed.value
}

// 生命周期
onMounted(() => {
  const clickHandler = (e) => {
    const container = document.querySelector('.model-selector-container')
    if (container && !container.contains(e.target)) {
      isModelMenuOpen.value = false
    }
  }
  document.addEventListener('click', clickHandler)

  // if (chartRef.value) {
  //   chart.value = echarts.init(chartRef.value)
  //   console.log("ECharts 初始化成功")
  // } else {
  //   console.warn("chartRef 为空，图表初始化失败")
  // }

  onBeforeUnmount(() => {
    document.removeEventListener('click', clickHandler)
  })


})

onMounted(() => {
  nickname.value = localStorage.getItem('nickname') || '用户'
})
onMounted(() => {
  console.log(currentConversationId)
})

const logout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('userId')
  localStorage.removeItem('nickname')
  router.push('/')
}

const selectConversation = ({ id, userMessage, fullMessages }) => {
  messages.value = [] // 清空原消息
  showModelTip.value = false
  currentConversationId=localStorage.getItem('convID')
  fullMessages.forEach((msg, index) => {
    if (msg.role === 'user') {
      messages.value.push({
        id: `user-${index}`,
        type: 'user',
        text: msg.content,
        avatar: userAvatar,
        // timestamp: new Date()
      })
    } else {
      messages.value.push({
        id: `${msg.role}-${index}`,
        type: 'bot',
        text: msg.content,
        avatar: updateAvatar(msg.role),
        model: msg.role,
        layoutClass: msg.modelCount,
        // timestamp: new Date(),
        rating: msg.score
      })
    }
  })

  nextTick(() => {
    scrollToBottom()
  })
}

const showGraphData = async () => {

  //buttonDisabled.value = false;
  chartVisible.value = true
}
const fetchGraphData = async () => {
  try {
    // 如果已有缓存数据直接使用
    // if (graphDataMap.value.has(currentQuestion.value)) {
    //   chartVisible.value = true
    //   return
    // }

    const response = await fetch('/getGraph', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({ question: currentQuestion.value }),
    })

    const result = await response.json()

    chartVisible.value = false
    buttonDisabled.value = false;
    // 存储图谱数据
    graphDataMap.value.set(currentQuestion.value, {
      nodes: processNodes(result),
      links: processLinks(result),
      categories: processCategories(result)
    })

    //chartVisible.value = true
  } catch (error) {
    console.error('获取图谱数据失败:', error)
  }
}

// 新增数据处理方法
const processNodes = (result) => {
  const entities = result.entities
  const add_entities = result.add_entities
  const combined_entities = [...entities, ...add_entities]

  const typeSet = [...new Set(entities.map(item => item[2]))]
  const colors = [
    '#FF6666', '#3399FF', '#66CC99', '#9966CC', '#FFCC00',
    '#FF9966', '#66CCCC', '#CC99FF', '#FF6699', '#99CC33'
  ]

  const categories = typeSet.map((item, index) => ({
    name: item,
    itemStyle: { color: colors[index % colors.length] },
  }))

  categories.push({
    name: '未知',
    itemStyle: { color: '#808080' }
  })

  return combined_entities.map(item => ({
    id: parseInt(item[0]),
    name: item[1],
    symbolSize: item[2] === '未知' ? 30 : 50,
    category: item[2] === '未知' ? categories.length - 1 : typeSet.indexOf(item[2]),
    tooltip: {
      //formatter: `{b}<br/>类型: {c}`,
      // 原始详细格式：
      formatter: `<div style="width:300px;white-space:normal">
        <strong>${item[1]}</strong><br/>
        类型: ${item[2]}<br/>
        描述: ${item[3]}
      </div>`
    },
    label: {
      show: item[2] !== '未知',
    }
  }))
}

const processLinks = (result) => {
  return result.relationships.map(item => ({
    source: parseInt(item[7]),
    target: parseInt(item[8]),
    label: item[4].length < 7 ? item[4] : '',
  }))
}
const processCategories = (result) => {
  const typeSet = [...new Set(result.entities.map(item => item[2]))]
  const colors = [
    '#FF6666', '#3399FF', '#66CC99', '#9966CC', '#FFCC00',
    '#FF9966', '#66CCCC', '#CC99FF', '#FF6699', '#99CC33'
  ]

  return [
    ...typeSet.map((item, index) => ({
      name: item,
      itemStyle: { color: colors[index % colors.length] }
    })),
    { name: '未知', itemStyle: { color: '#808080' } }
  ]
}

// 完整的弹窗处理方法
const onDialogOpened = () => {

  nextTick(() => {
    if (!chartRef.value) {
      console.error('图表容器未找到！');
      return;
    }
    if (!chartInstance) {
      chartInstance = echarts.init(chartRef.value);
    }
    // 获取当前问题的数据
    const currentData = graphDataMap.value.get(currentQuestion.value)

    if (currentData) {
      const option = {
        //title: { text: '知识图谱', left: 'center' },
        tooltip: {
          formatter: (params) => {
            return params.dataType === 'edge'
                ? `关系: ${params.data.label}`
                : `实体: ${params.data.name}`
          }
        },
        legend: [{
          data: currentData.categories.map(c => c.name),
          //selectedMode: 'single'
        }],
        series: [{
          type: 'graph',
          layout: 'force',
          symbolSize: 50,
          roam: true,
          label: { show: true },
          edgeLabel: {
            show: true,
            // formatter: '{@label}'
            formatter: (x) => {
              const label = x.data.label || ''
              return label.length < 7 ? label : ''
            }
          },
          data: currentData.nodes,
          links: currentData.links,
          categories: currentData.categories,
          force: {
            repulsion: 200,
            edgeLength: [80, 200]
          }
        }]
      }

      chartInstance.clear()
      chartInstance.setOption(option)
      chartInstance.resize()

    }
  });

}

//语音播报
const speakText = (message) => {
  const text = Array.isArray(message.text) ? message.text.join('') : message.text
  if (!text) return

  // 当前是这条消息正在播放，切换暂停/继续
  if (message.isSpeaking) {
    if (speechSynthesis.paused) {
      speechSynthesis.resume()
      message.isPaused = false
    } else {
      speechSynthesis.pause()
      message.isPaused = true
    }
    return
  }

  // 正在播放其他消息内容
  speechSynthesis.cancel()
  resetAllSpeechStatus()

  const newUtterance = new SpeechSynthesisUtterance(text)
  newUtterance.lang = 'zh-CN'
  newUtterance.rate = 1
  newUtterance.pitch = 1

  newUtterance.onend = () => {
    message.isSpeaking = false
    message.isPaused = false
    currentSpeakingMessageId = null
  }

  message.isSpeaking = true
  message.isPaused = false
  currentUtterance = newUtterance
  currentSpeakingMessageId = message.id

  speechSynthesis.speak(newUtterance)
}

const stopSpeech = (message) => {
  speechSynthesis.cancel()
  message.isSpeaking = false
  message.isPaused = false
  currentSpeakingMessageId = null
}
const resetAllSpeechStatus = () => {
  if (currentSpeakingMessageId) {
    const msg = messages.value.find(m => m.id === currentSpeakingMessageId)
    if (msg) {
      msg.isSpeaking = false
      msg.isPaused = false
    }
  }
}


</script>

<style scoped>
/* 整体聊天容器样式 */
.chat-container {
  display: flex;
  flex-direction: row;
  height: 100vh;
  background-color: #f0f2f5;
  transition: background-color 0.3s;
  opacity: 0;
  animation: fadeIn 1s forwards;
}


/* 顶部导航栏样式 */
.top-navbar {
  width: 100%;
  background-color: #480000;
  padding: 20px 20px; /* 增加 padding 以增加高度 */
  position: fixed;
  top: 0;
  left: 0;
  z-index: 1000;
  color: white;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.1);
}

.navbar-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.navbar-logo {
  font-size: 24px;
  font-weight: bold;
  display: flex;
  align-items: center;
}

.logo-image {
  width: 24px;
  height: 24px;
  margin-right: 8px;
}

.navbar-links {
  display: flex;
  list-style: none;
  margin: 0;
  padding: 0;
}

.navbar-links li {
  margin-left: 20px;
  cursor: pointer;
  font-size: 16px;
}

.navbar-links a {
  color: white;
  text-decoration: none;
}

.navbar-links a:hover {
  color: #FFD700;
  transform: scale(1.1);
}


/* 左侧功能栏样式 */
.sidebar {
  width: 250px;
  color: #fff;
  display: flex;
  flex-direction: column;
  transition: width 0.3s, background-color 0.3s;
  overflow: hidden;
  margin-top: 70px; /* 根据导航栏的新高度调整 */
  animation: slideIn 0.5s ease-out;

  /* 使用背景图片 */
  background-image: url('../../public/assets/img/leftbar.jpg');
  background-size: cover; /* 背景图片适应功能栏大小 */
  background-position: center; /* 让背景图片居中 */
  background-repeat: no-repeat; /* 防止背景图片重复 */
}

.sidebar.collapsed {
  width: 50px;
}

.sidebar-toggle {
  padding: 15px;
  cursor: pointer;
  background-color: #3D0000;
  text-align: center;
}

.sidebar-content {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 15px;
}

.feedback {
  margin-top: auto; /* 固定到底部 */
  padding-top: 10px; /* 根据实际需要微调 */
  height: 40px; /* 控制高度，只显示一行文字 */
  line-height: 40px; /* 垂直居中 */
  font-size: 20px;
  font-family: 'LiSu', sans-serif;
  text-align: center;
  cursor: pointer;
  transition: background-color 0.3s;
}

.feedback:hover {
  background-color: #3D0000;
}


.sidebar-content ul {
  list-style: none;
  padding: 0;
}

.sidebar-content ul li {
  margin: 20px 0;
  cursor: pointer;
  font-size: 24px; /* 增加字体大小 */
  transition: background-color 0.3s;
  font-family: 'LiSu', sans-serif; /* 设置字体为隶书 */
}

.sidebar-content ul li:hover {
  background-color: #3D0000;
  font-size: 24px; /* 悬停时保持字体大小 */
  font-family: 'LiSu', sans-serif; /* 悬停时保持隶书字体 */
}


.sidebar-content ul li i {
  margin-right: 10px;
}


/* 背景容器，包括渐变和图片 */
.background-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  flex: 1;
  background: linear-gradient(to top, rgba(255, 255, 255, 0.7), rgba(255, 255, 255, 0.7)),
  url('../../public/assets/img/modelBackground1.JPG') no-repeat center center fixed;
  background-size: cover;
  animation: backgroundFadeIn 2s ease-out;
}


/* 聊天消息显示区 */
.chat-messages {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 80px; /* 调整为 120px 以避免遮盖 */
  animation: messageFadeIn 1s ease-out;
}

/* 单条消息样式 */
.message {
  display: flex;
  align-items: flex-start;
  width: 100%;
  animation: fadeIn 0.5s ease-in-out;
  /*flex-direction: column;  让内容按列排列，评分栏+头像在上，消息在下 */
  gap: 10px; /* 评分栏和消息内容之间的间距 */
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 头像容器样式 */
.avatar-container {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  width: 40px;
}

.bot-m {
  display: flex;
  flex-direction: row; /* 横向排列多个模型回答 */
  justify-content: space-between; /* 平分空间 */
  width: 100%;
  gap: 10px;
  margin-bottom: 10px;
}

/* 用户消息样式 */
.message.user {
  justify-content: flex-end;
  text-align: right; /* 确保文字从右边开始 */
}

.user .avatar-container {
  order: 2;
  margin-left: 10px; /* 头像与消息之间的间隔 */
}

/* 用户消息：让头像在右，消息在左 */
.user-message-container {
  display: flex;
  align-items: center;
  justify-content: flex-end; /* 右对齐 */
  gap: 10px;
  float: right;
  margin-left: auto;
}

.user-message {
  background-color: #f1f0f0; /* 修改为适合的背景颜色 */
  color: #000;
  padding: 10px 15px;
  border-radius: 20px;
  max-width: 70%;
  font-size: 16px;
  position: relative;
  margin-left: 10px; /* 向左移动10px，避免和头像太近 */
  white-space: pre-wrap; /* 保留换行符 */
  display: flex;
  flex-direction: column; /* 让内容和时间戳垂直排列 */
  text-align: left;
}

.dark-mode .user .user-message {
  background-color: #3D0000;
}

/* 机器人消息样式 */
.bot {
  justify-content: flex-start;
}

.bot .avatar-container {
  order: 0;
}

.bot-message {
  color: #000;
  padding: 10px 15px;
  border-radius: 20px;
  max-width: 90%;
  font-size: 16px;
  position: relative;
  margin-right: 10px;
  white-space: pre-wrap; /* 保留换行符 */

}

.dark-mode .bot .bot-message {
  background-color: #333;
  color: #fff;
}

/* 头像样式 */
.avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
}

/* 消息文本样式 */
.message-text {
  max-width: 100%;
  word-wrap: break-word;
}

/* 消息显示图片 */
.gua-image {
  max-width: 100px;
  height: auto;
  margin-top: 10px;
  border-radius: 8px;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.2);
}


/* 消息时间戳样式
.timestamp {
  font-size: 12px;
  color: #aaa;
  margin-top: 5px;
  text-align: right;
  display: block;
  flex-wrap: wrap;
  clear: both
}*/

/* 机器人正在输入提示 */
.typing-indicator {
  font-style: italic;
  color: #aaa;
  padding-left: 50px;
  margin-bottom: 10px;
}

/* 输入区和按钮容器 */
.chat-input-container {
  display: flex;
  align-items: center;
  padding: 15px;
  border-top: 1px solid #ddd;
  background-color: #fff;
  animation: slideUp 0.5s ease-out;
}

/* 输入框美化 */
.input-box {
  flex: 1;
  display: flex;
  align-items: center;
  background: #D3D3D3;
  border-radius: 50px;
  padding: 12px 20px;
  border: 1px solid #ddd;
  transition: background-color 0.3s;
}

.chat-input-container input {
  border: none;
  outline: none;
  background: transparent;
  flex: 1;
  font-size: 16px;
  padding-left: 10px;
}

.chat-input-container input::placeholder {
  color: #aaa;
}


/* 发送按钮美化 */
.send-button {
  padding: 10px 20px;
  background-color: #5C0000;
  color: white;
  border: none;
  border-radius: 50px;
  margin-left: 10px;
  cursor: pointer;
  transition: background-color 0.3s ease, transform 0.1s ease;
}

.send-button:hover {
  background-color: #3B0000;
  transform: scale(1.05);
}

.send-button:disabled {
  background-color: #999999;
  cursor: not-allowed;
}


.dark-mode .send-button {
  background-color: #3B0000;
}

.dark-mode .send-button:hover {
  background-color: #5C0000;
}

/* 夜间模式按钮 */
.toggle-darkmode {
  padding: 10px 20px;
  margin-left: 10px;
  background-color: #444;
  color: white;
  border: none;
  border-radius: 50px;
  cursor: pointer;
}

.dark-mode .toggle-darkmode {
  background-color: #777;
}

.toggle-darkmode:hover {
  background-color: #555;
  transform: scale(1.05);
}

/* 动画效果 */
@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes slideIn {
  from {
    transform: translateX(-100%);
  }
  to {
    transform: translateX(0);
  }
}

@keyframes backgroundFadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes messageFadeIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes slideUp {
  from {
    transform: translateY(20px);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

/* 新增模型选择器样式 */
.model-selector-container {
  position: relative;
  margin: 10px 20px;
  z-index: 100;
}

.model-select-button {
  padding: 8px 20px;
  background-color: #5C0000;
  color: white;
  border: none;
  border-radius: 20px;
  cursor: pointer;
  transition: all 0.3s ease;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.model-select-button:hover {
  background-color: #3B0000;
  transform: translateY(-1px);
}

.dark-mode .model-select-button {
  background-color: #3B0000;
}

.dark-mode .model-select-button:hover {
  background-color: #5C0000;
}

.model-menu {
  position: absolute;
  bottom: calc(100% + 8px);
  left: 0;
  background-color: white;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  min-width: 160px;
  overflow: hidden;
  transform: translateY(-2px);
  animation: menuSlideDown 0.2s ease-out;
}

@keyframes menuSlideDown {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(-2px);
  }
}


.model-menu-item {
  padding: 12px 16px;
  cursor: pointer;
  transition: all 0.2s ease;
  color: #333;
  font-size: 14px;
}

.model-menu-item:hover {
  background-color: rgba(240, 240, 240, 0.6); /* 变浅并加透明度 */
  color: #333;
}


.model-select-button.selected-model {
  background-color: #8B0000; /* 如果模型被选中时，按钮背景颜色加深 */
  color: white;
}

.model-menu-item.selected {
  background-color: #660000; /* 如果菜单项被选中时，背景颜色加深 */
  color: white;
}

.model-menu-item.selected:hover {
  background-color: #8B0000; /* 如果菜单项被选中时，背景颜色加深 */
  color: white;
}

.model-menu-item {
  cursor: pointer;
  padding: 10px;
  background-color: #fff;
  color: #333;
}

/*.model-menu-item:hover {
//  background-color: #ddd;
}*/

.model-menu-item.disabled {
  color: #999;
  pointer-events: none;
  cursor: not-allowed;
  opacity: 0.5
}

.rating-avatar-container {
  display: flex;
  align-items: center; /* 让头像和评分栏垂直居中对齐 */
  justify-content: flex-start; /* 让评分栏和头像在一行显示 */
  gap: 10px; /* 控制评分栏和头像之间的间距 */
}

.rating {
  display: flex;
  align-items: center; /* 让文字和星星垂直居中 */
  margin-top: 10px;

}

.rating-label {
  margin-right: 5px;
  color: #808080;
  font-size: 13px;
}

.star {
  font-size: 20px;
  cursor: pointer;
  color: #808080; /* 默认灰色 */
  transition: transform 0.2s, color 0.2s;
}

.star:hover {
  transform: scale(1.2);
  /*color: #FFD700; 鼠标悬停时变金色 */
}

.rating span {
  margin-right: 5px;
}

.stars {
  display: flex;
  align-items: center;
}

.stars-with-rating {
  display: flex;
  align-items: center;
}

.filled {
  color: #FFD700 !important; /* 确保覆盖默认颜色 */
}

.rating-score {
  font-size: 16px;
  margin-left: 5px; /* 调整星星和评分数字之间的间距 */
  font-weight: bold;
}

.model-tip {
  text-align: center;
  font-size: 24px;
  color: white; /* 白色文字 */
  background: linear-gradient(135deg, #3D0000, #6C0000); /* 深红色渐变背景 */
  padding: 16px 22px;
  border-radius: 12px;
  margin: 20px auto; /* 居中 */
  width: fit-content;
  max-width: 85%;
  font-family: 'LiSu', sans-serif; /* 隶书风格 */
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2); /* 柔和阴影 */
  animation: fadeInTip 1s ease-out, slideUp 0.8s ease-out, textAppear 1.2s ease-out; /* 添加渐变和滑动动画 + 文字出现动画 */
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  transform: translateY(20px);
  line-height: 1.6;
}

.model-tip i {
  font-size: 24px;
  margin-right: 10px; /* 让图标和文字有些间距 */
}

@keyframes fadeInTip {
  0% {
    opacity: 0;
    transform: translateY(-20px);
  }
  100% {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes slideUp {
  0% {
    transform: translateY(20px);
  }
  100% {
    transform: translateY(0);
  }
}

@keyframes textAppear {
  0% {
    opacity: 0;
  }
  100% {
    opacity: 1;
  }
}

/* 夜间模式适配 */
.dark-mode .model-tip {
  background: linear-gradient(135deg, #6c0000, #3D0000); /* 深红色渐变背景 */
}

/* 当选择多个模型时，应用横向布局 */
.chat-messages.horizontal-layout {
  display: flex;
  flex-direction: row; /* 设置为横向排列 */
  flex-wrap: wrap; /* 允许换行 */
  gap: 20px; /* 设置模型回答之间的间距 */
  justify-content: space-between; /* 平均分配空间 */
}

/* 机器人消息的样式，确保每个回答在横向布局下占据一定宽度 */
.message.bot {
  display: flex;
  flex-direction: column;
  width: 100%; /* 默认情况下占满一行 */
  align-items: center;
  margin-bottom: 20px; /* 每个模型之间的垂直间距 */
}

.chat-messages .message.one-model {
  width: 90%;
  margin: 0 auto; /* 居中 */
  /*text-align: center; /* 可选：文字也居中 */
}


.chat-messages .message.two-models {
  width: 48%;
}

.chat-messages .message.three-models {
  width: 30%;
}


/* 头像容器样式 */
.avatar-container {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 40px;
  margin-right: 10px;
}

/* 机器人消息内容 */
.message.bot .bot-message {
  background-color: #f1f0f0;
  padding: 10px;
  border-radius: 10px;
  max-width: 100%;
  text-align: left;
  white-space: pre-wrap; /* 保留换行符 */
}

/* 评分栏样式 */
.rating {
  margin-top: 10px;
  text-align: center;
}

/* 头像和评分栏垂直排列 */
.message.bot .rating-avatar-container {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
}

/* 用户消息的样式保持不变 */
.user-message-container {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  margin-left: auto;
}

.user-message {
  background-color: #f1f0f0;
  color: #000;
  padding: 10px 15px;
  border-radius: 20px;
  max-width: 70%;
  font-size: 16px;
  position: relative;
  margin-left: 10px;
  white-space: pre-wrap;
  display: flex;
  flex-direction: column;
}

/* 消息布局适配 */


.navbar-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.navbar-user-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.nickname {
  font-size: 16px;
  font-weight: bold;
  color: #ffffff;
}

.logout-button {
  background-color: #b22222;
  border: none;
  color: white;
  padding: 6px 12px;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 0.3s;
}

.logout-button:hover {
  background-color: #8b0000;
}

/*知识图谱相关*/
.chart-box {
  width: 100%;
  height:700px;
}

.graph-button-container {
  display: flex;
  justify-content: flex-end;
  margin:0 20px 0 ;
}

.load-btn {
  background-color: #4a90e2;
  color: white;
  padding: 8px 16px;
  font-size: 14px;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.3s ease;
}

.load-btn:hover {
  background-color: #357ab8;
}

.load-btn:disabled,
.load-btn.is-disabled {
  background-color: #dcdfe6 !important; /* 灰色背景 */
  color: #c0c4cc !important;            /* 文字也灰一点 */
  cursor: not-allowed !important;        /* 鼠标禁止图标 */
  border-color: #ebeef5 !important;      /* 边框也淡一点 */
}
/*语音播报*/
.voice-button-container {
  margin-top: 10px;
}

.voice-button {
  background-color: #f0f0f0;
  border: 1px solid #ccc;
  border-radius: 6px;
  padding: 4px 10px;
  margin-top: 6px;
  cursor: pointer;
  font-size: 14px;
}

.voice-button:hover {
  background-color: #e8e8e8;
}


</style>
