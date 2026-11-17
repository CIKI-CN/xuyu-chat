package app.xuyu.chat

import app.xuyu.chat.data.ApiConfig
import app.xuyu.chat.data.Appearance
import app.xuyu.chat.data.ChatConfig
import app.xuyu.chat.data.Persona
import app.xuyu.chat.data.Quota
import app.xuyu.chat.data.SearchConfig
import app.xuyu.chat.data.Settings

/** 测试里用：按「正常模式一张卡」拼 Settings */
fun settings(
    persona: Persona,
    api: ApiConfig = ApiConfig(),
    appearance: Appearance = Appearance(),
    quota: Quota = Quota(),
    search: SearchConfig = SearchConfig(),
    chat: ChatConfig = ChatConfig()
): Settings = Settings(
    mode = Settings.MODE_NORMAL,
    personas = listOf(persona),
    activePersonaId = persona.id,
    api = api,
    appearance = appearance,
    quota = quota,
    search = search,
    chat = chat
)
