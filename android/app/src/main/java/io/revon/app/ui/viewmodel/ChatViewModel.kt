package io.revon.app.ui.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.revon.app.data.model.Club
import io.revon.app.data.model.ClubChatMessage
import io.revon.app.data.model.ClubEvent
import io.revon.app.data.model.ClubMember
import io.revon.app.data.model.MemberRole
import io.revon.app.data.model.MemberStatus
import io.revon.app.data.repository.ApiChatRepository
import io.revon.app.data.repository.ApiClubRepository
import io.revon.app.di.NetworkModule
import io.revon.app.utils.NetworkErrorUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val apiClubRepository = ApiClubRepository(NetworkModule.apiService)
    private val apiChatRepository = ApiChatRepository(NetworkModule.apiService)

    private val _clubs = MutableStateFlow<List<Club>>(emptyList())
    val clubs: StateFlow<List<Club>> = _clubs.asStateFlow()

    private val _selectedClub = MutableStateFlow<Club?>(null)
    val selectedClub: StateFlow<Club?> = _selectedClub.asStateFlow()

    private val _messages = MutableStateFlow<List<ClubChatMessage>>(emptyList())
    val messages: StateFlow<List<ClubChatMessage>> = _messages.asStateFlow()

    private val _members = MutableStateFlow<List<ClubMember>>(emptyList())
    val members: StateFlow<List<ClubMember>> = _members.asStateFlow()

    private val _events = MutableStateFlow<List<ClubEvent>>(emptyList())
    val events: StateFlow<List<ClubEvent>> = _events.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var pollingJob: kotlinx.coroutines.Job? = null

    init {
        fetchClubs()
    }

    private fun fetchClubs() {
        viewModelScope.launch {
            val res = apiClubRepository.getClubs()
            if (res.isSuccess) {
                val updatedClubs = res.getOrNull() ?: emptyList()
                _clubs.value = updatedClubs
                if (_selectedClub.value == null && updatedClubs.isNotEmpty()) {
                    selectClub(updatedClubs.first())
                }
            } else {
                Log.e("ChatViewModel", "Error fetching clubs", res.exceptionOrNull())
            }
        }
    }

    fun selectClub(club: Club, currentNickname: String? = null) {
        _selectedClub.value = club
        startMessagePolling(club.id.toString())
        fetchEvents(club.id)
        fetchMembers(club.id, currentNickname ?: club.captainNickname ?: "車手")
    }

    private fun fetchMembers(clubId: Int, currentNickname: String) {
        viewModelScope.launch {
            val res = apiClubRepository.getClubMembers(clubId)
            if (res.isSuccess) {
                val rawList = res.getOrNull() ?: emptyList()
                val parsed = rawList.map { item: Map<String, Any> ->
                    val nick = (item["memberNickname"] ?: item["member_nickname"] ?: item["nickname"] ?: "隊員").toString()
                    val roleStr = (item["role"] ?: "member").toString()
                    val roleEnum = if (roleStr.lowercase() == "captain") MemberRole.CAPTAIN else MemberRole.MEMBER
                    val joinedAtStr = (item["joinedAt"] ?: item["joined_at"] ?: "").toString()
                    ClubMember(
                        clubId = clubId.toString(),
                        clubName = _selectedClub.value?.name,
                        status = MemberStatus.JOINED,
                        memberNickname = nick,
                        role = roleEnum,
                        joinedAt = joinedAtStr.ifBlank { null }
                    )
                }
                
                // 確保目前登入者或隊長在列表中顯示
                val hasSelfOrCap = parsed.any { it.memberNickname == currentNickname || it.role == MemberRole.CAPTAIN }
                val finalList = if (!hasSelfOrCap && currentNickname.isNotBlank()) {
                    parsed + ClubMember(
                        clubId = clubId.toString(),
                        clubName = _selectedClub.value?.name,
                        status = MemberStatus.JOINED,
                        memberNickname = currentNickname,
                        role = MemberRole.MEMBER
                    )
                } else {
                    parsed
                }
                _members.value = finalList
            } else {
                // Fallback 確保使用者能在成員列表中看到自己與隊長
                _members.value = listOf(
                    ClubMember(
                        clubId = clubId.toString(),
                        clubName = _selectedClub.value?.name,
                        status = MemberStatus.JOINED,
                        memberNickname = currentNickname,
                        role = MemberRole.CAPTAIN
                    )
                )
            }
        }
    }

    private fun fetchEvents(clubId: Int) {
        viewModelScope.launch {
            val res = apiClubRepository.getClubEvents(clubId)
            if (res.isSuccess) {
                val rawList = res.getOrNull() ?: emptyList()
                val parsed = rawList.map { item: Map<String, Any> ->
                    val rawId = item["id"]
                    val numericId = when (rawId) {
                        is Number -> rawId.toInt()
                        is String -> rawId.toDoubleOrNull()?.toInt() ?: rawId.toIntOrNull() ?: 0
                        else -> 0
                    }
                    val rawParticipants = (item["participants"] ?: "").toString()
                    val participantsList = rawParticipants.split(",").map { it.trim() }.filter { it.isNotBlank() }

                    ClubEvent(
                        id = numericId.toString(),
                        clubId = clubId.toString(),
                        title = (item["title"] ?: "車隊活動").toString(),
                        trackCode = (item["track_code"] ?: item["trackCode"] ?: "136").toString(),
                        dateTimeDisplay = (item["event_date"] ?: item["eventDate"] ?: "即將舉行").toString(),
                        description = (item["description"] ?: "").toString(),
                        creatorNickname = (item["creator_nickname"] ?: item["creatorNickname"] ?: "隊長").toString(),
                        creatorUid = (item["creator_uid"] ?: item["creatorUid"] ?: "").toString(),
                        participants = participantsList,
                        participantCount = if (participantsList.isNotEmpty()) participantsList.size else 1
                    )
                }
                Log.d("ChatViewModel", "fetchEvents: loaded ${parsed.size} events for club $clubId")
                _events.value = parsed
            } else {
                Log.e("ChatViewModel", "fetchEvents error", res.exceptionOrNull())
                _events.value = emptyList()
            }
        }
    }

    private fun startMessagePolling(clubId: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                val res = apiChatRepository.getMessages(clubId)
                if (res.isSuccess) {
                    val fetched = res.getOrNull() ?: emptyList()
                    _messages.value = fetched
                    Log.d("ChatViewModel", "startMessagePolling: fetched ${fetched.size} messages for clubId $clubId")
                } else {
                    Log.e("ChatViewModel", "startMessagePolling failed for clubId $clubId", res.exceptionOrNull())
                }
                delay(3000) // Poll every 3 seconds
            }
        }
    }

    fun sendMessage(senderNickname: String, text: String, senderUid: String = "") {
        if (text.isBlank()) return
        val currentClub = _selectedClub.value ?: return

        val clubIdParam = currentClub.id.toString()
        Log.d("ChatViewModel", "sendMessage: sender=$senderNickname, clubId=$clubIdParam, body=$text")

        // 建立待發送訊息
        val msg = ClubChatMessage(
            clubId = clubIdParam,
            authorNickname = senderNickname,
            authorUid = senderUid,
            body = text,
            messageType = "text"
        )
        
        // 1. 即時樂觀更新 (Optimistic UI Update) 讓使用者傳送後立刻在聊天室看到
        val optimisticMsg = msg.copy(
            id = "temp_${System.currentTimeMillis()}",
            authorNickname = senderNickname
        )
        _messages.value = _messages.value + optimisticMsg

        viewModelScope.launch {
            val res = apiChatRepository.sendMessage(clubIdParam, msg)
            if (res.isFailure) {
                Log.e("ChatViewModel", "sendMessage API failed", res.exceptionOrNull())
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "發送訊息失敗"
            } else {
                Log.d("ChatViewModel", "sendMessage API succeeded, fetching latest messages...")
                val fetchRes = apiChatRepository.getMessages(clubIdParam)
                if (fetchRes.isSuccess) {
                    _messages.value = fetchRes.getOrNull() ?: emptyList()
                }
            }
        }
    }

    fun deleteMessage(msg: ClubChatMessage) {
        _messages.value = _messages.value.filter { 
            if (!msg.id.isNullOrBlank() && !it.id.isNullOrBlank()) {
                it.id != msg.id
            } else {
                !(it.body == msg.body && it.authorNickname == msg.authorNickname)
            }
        }
        val currentClub = _selectedClub.value ?: return
        val clubIdParam = currentClub.id.toString()
        viewModelScope.launch {
            val res = apiChatRepository.deleteMessage(clubIdParam, msg)
            if (res.isFailure) {
                Log.e("ChatViewModel", "Error deleting message", res.exceptionOrNull())
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "刪除訊息失敗"
            } else {
                val fetchRes = apiChatRepository.getMessages(clubIdParam)
                if (fetchRes.isSuccess) {
                    _messages.value = fetchRes.getOrNull() ?: emptyList()
                }
            }
        }
    }

    // TODO: Other methods need corresponding APIs
    fun createClub(name: String, badge: String, motto: String, captain: String) {
        viewModelScope.launch {
            val result = apiClubRepository.createClub(name, motto)
            if (result.isSuccess) {
                fetchClubs() // Refresh the list
            } else {
                _errorMessage.value = NetworkErrorUtils.getFriendlyErrorMessage(result.exceptionOrNull(), "建立車隊失敗")
            }
        }
    }

    fun removeMember(memberNickname: String) {
        val currentClub = _selectedClub.value ?: return
        viewModelScope.launch {
            val result = apiClubRepository.manageClubMember("remove", currentClub.id, 0)
            if (result.isFailure) {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "移除成員失敗"
            }
        }
    }

    fun setMemberRole(memberNickname: String, role: io.revon.app.data.model.MemberRole) {
        val currentMembers = _members.value.toMutableList()
        val index = currentMembers.indexOfFirst { it.memberNickname == memberNickname }
        if (index >= 0) {
            currentMembers[index] = currentMembers[index].copy(role = role)
            _members.value = currentMembers
        }
    }

    fun inviteMember(nickname: String) {
        val currentClub = _selectedClub.value ?: return
        viewModelScope.launch {
            val result = apiClubRepository.manageClubMember("join", currentClub.id, 0, inviteCode = nickname)
            if (result.isFailure) {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "加入車隊失敗"
            }
        }
    }

    fun updateClubSettings(name: String, badge: String, motto: String, accentColor: String = "#E10600", onComplete: ((Boolean) -> Unit)? = null) {
        val currentClub = _selectedClub.value ?: return
        viewModelScope.launch {
            val result = apiClubRepository.updateClub(
                clubId = currentClub.id,
                name = name,
                badgeLetters = badge,
                motto = motto,
                accentColor = accentColor
            )
            if (result.isSuccess) {
                val updated = result.getOrNull()
                if (updated != null) {
                    _selectedClub.value = updated
                    _clubs.value = _clubs.value.map { if (it.id == updated.id) updated else it }
                }
                onComplete?.invoke(true)
            } else {
                _errorMessage.value = NetworkErrorUtils.getFriendlyErrorMessage(result.exceptionOrNull(), "更新車隊設定失敗")
                onComplete?.invoke(false)
            }
        }
    }

    fun deleteClub(clubId: String) {
        _errorMessage.value = "尚未實作: API endpoint required"
    }

    fun deleteClubEvent(eventId: String) {
        val currentClub = _selectedClub.value ?: return
        val idInt = eventId.toDoubleOrNull()?.toInt() ?: eventId.toIntOrNull() ?: run {
            Log.e("ChatViewModel", "deleteClubEvent failed: invalid eventId format '$eventId'")
            return
        }
        Log.d("ChatViewModel", "deleteClubEvent: deleting event idInt=$idInt (raw eventId=$eventId) for club ${currentClub.id}")
        viewModelScope.launch {
            val res = apiClubRepository.deleteClubEvent(idInt)
            if (res.isSuccess) {
                Log.d("ChatViewModel", "deleteClubEvent succeeded for event $idInt, refreshing events...")
                fetchEvents(currentClub.id)
            } else {
                val errStr = res.exceptionOrNull()?.message ?: "刪除活動失敗"
                Log.e("ChatViewModel", "deleteClubEvent error: $errStr")
                _errorMessage.value = errStr
            }
        }
    }

    fun toggleEventJoin(eventId: String, userIdentifier: String) {
        val currentClub = _selectedClub.value ?: return
        val idInt = eventId.toDoubleOrNull()?.toInt() ?: eventId.toIntOrNull() ?: run {
            Log.e("ChatViewModel", "toggleEventJoin failed: invalid eventId format '$eventId'")
            return
        }
        Log.d("ChatViewModel", "toggleEventJoin: event $idInt by user $userIdentifier")
        viewModelScope.launch {
            val res = apiClubRepository.toggleEventJoin(idInt, userIdentifier)
            if (res.isSuccess) {
                fetchEvents(currentClub.id)
            } else {
                _errorMessage.value = res.exceptionOrNull()?.message ?: "更新報名狀態失敗"
            }
        }
    }

    fun createClubEvent(
        title: String,
        trackCode: String,
        dateTimeDisplay: String,
        description: String = "",
        creatorNickname: String = "隊長",
        creatorUid: String = ""
    ) {
        val currentClub = _selectedClub.value ?: return
        viewModelScope.launch {
            val result = apiClubRepository.createClubEvent(
                clubId = currentClub.id,
                title = title,
                description = description.ifBlank { "Track: $trackCode" },
                eventDate = dateTimeDisplay
            )
            if (result.isSuccess) {
                fetchEvents(currentClub.id)
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "建立活動失敗"
            }
        }
    }

    fun updateCaptainNickname(oldNickname: String, newNickname: String) {
        _errorMessage.value = "尚未實作: API endpoint required"
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun copyErrorToClipboard(context: Context, errText: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("RevOn Connection Error", errText)
        clipboard.setPrimaryClip(clip)
        io.revon.app.ui.components.RevonToastManager.info("已複製錯誤訊息至剪貼簿！")
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
