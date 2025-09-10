package com.example.feature_browser.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator // ローディング表示用
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton // 接続テスト用にOutlinedButtonも良いかも
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NasConnectionEditorScreen(
    viewModel: SettingsViewModel,
    connectionId: String?,
    onNavigateUp: () -> Unit
) {
    val uiState by viewModel.editorUiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.loadConnectionForEdit(connectionId)
    }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            onNavigateUp()
        }
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.userMessageShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (connectionId == null) "NAS接続の追加" else "NAS接続の編集") },
                navigationIcon = {
                    IconButton(onClick = {
                        // 保存成功時以外（編集中など）はViewModelの状態をリセットすることが望ましい場合がある
                        if (!uiState.saveSuccess) viewModel.loadConnectionForEdit(null) // 新規状態に戻すなど
                        onNavigateUp()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { paddingValues ->
        NasConnectionEditorForm(
            modifier = Modifier.padding(paddingValues),
            uiState = uiState,
            onNicknameChange = { viewModel.updateEditorState(nickname = it) },
            onHostnameChange = { viewModel.updateEditorState(hostname = it) },
            onPathChange = { viewModel.updateEditorState(path = it) },
            onUsernameChange = { viewModel.updateEditorState(username = it) },
            onPasswordChange = { viewModel.updateEditorState(password = it) },
            onTestConnectionClick = { viewModel.testNasConnection() },
            onSaveClick = { viewModel.saveConnection() }
        )
    }
}

@Composable
fun NasConnectionEditorForm(
    modifier: Modifier = Modifier,
    uiState: NasEditorUiState,
    onNicknameChange: (String) -> Unit,
    onHostnameChange: (String) -> Unit,
    onPathChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTestConnectionClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        OutlinedTextField(
            value = uiState.nickname,
            onValueChange = onNicknameChange,
            label = { Text("ニックネーム (必須)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = uiState.userMessage != null && uiState.nickname.isBlank() // 簡単なバリデーション例
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = uiState.hostname,
            onValueChange = onHostnameChange,
            label = { Text("ホスト名またはIPアドレス (必須)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = uiState.userMessage != null && uiState.hostname.isBlank()
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = uiState.path,
            onValueChange = onPathChange,
            label = { Text("共有フォルダ名 (必須)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = uiState.userMessage != null && uiState.path.isBlank()
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = uiState.username,
            onValueChange = onUsernameChange,
            label = { Text("ユーザー名 (オプション)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = uiState.password,
            onValueChange = onPasswordChange,
            label = { Text("パスワード (オプション)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = "パスワード表示切替")
                }
            }
        )
        Spacer(Modifier.height(32.dp))

        // 接続テストボタン
        OutlinedButton( // 通常のButtonでも良い
            onClick = onTestConnectionClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isTestingConnection && !uiState.isSaving // テスト中や保存中は無効
        ) {
            if (uiState.isTestingConnection) {
                CircularProgressIndicator(
                    modifier = Modifier.height(24.dp).width(24.dp), // サイズ調整
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text("テスト中...")
            } else {
                Text("接続テスト")
            }
        }
        Spacer(Modifier.height(16.dp)) // ボタン間のスペース

        // 保存ボタン
        Button(
            onClick = onSaveClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isSaving && !uiState.isTestingConnection // 保存中やテスト中は無効
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.height(24.dp).width(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary // ボタンテキスト色に合わせる
                )
                Spacer(Modifier.width(8.dp))
                Text("保存中...")
            } else {
                Text("保存")
            }
        }
    }
}

// --- Preview ---
@Preview(name = "Default State", showBackground = true)
@Composable
fun NasConnectionEditorFormPreview_Default() {
    MaterialTheme { // AppTheme を使うのが望ましい
        NasConnectionEditorForm(
            uiState = NasEditorUiState(),
            onNicknameChange = {},
            onHostnameChange = {},
            onPathChange = {},
            onUsernameChange = {},
            onPasswordChange = {},
            onTestConnectionClick = {},
            onSaveClick = {}
        )
    }
}

@Preview(name = "Testing Connection", showBackground = true)
@Composable
fun NasConnectionEditorFormPreview_Testing() {
    MaterialTheme {
        NasConnectionEditorForm(
            uiState = NasEditorUiState(isTestingConnection = true),
            onNicknameChange = {},
            onHostnameChange = {},
            onPathChange = {},
            onUsernameChange = {},
            onPasswordChange = {},
            onTestConnectionClick = {},
            onSaveClick = {}
        )
    }
}

@Preview(name = "Saving State", showBackground = true)
@Composable
fun NasConnectionEditorFormPreview_Saving() {
    MaterialTheme {
        NasConnectionEditorForm(
            uiState = NasEditorUiState(isSaving = true),
            onNicknameChange = {},
            onHostnameChange = {},
            onPathChange = {},
            onUsernameChange = {},
            onPasswordChange = {},
            onTestConnectionClick = {},
            onSaveClick = {}
        )
    }
}

@Preview(name = "With User Message (Error)", showBackground = true)
@Composable
fun NasConnectionEditorFormPreview_UserMessageError() {
    MaterialTheme {
        NasConnectionEditorForm(
            uiState = NasEditorUiState(
                userMessage = "ホスト名を入力してください",
                hostname = "" // エラー状態を再現
            ),
            onNicknameChange = {},
            onHostnameChange = {},
            onPathChange = {},
            onUsernameChange = {},
            onPasswordChange = {},
            onTestConnectionClick = {},
            onSaveClick = {}
        )
    }
}

@Preview(name = "Filled Form", showBackground = true)
@Composable
fun NasConnectionEditorFormPreview_Filled() {
    MaterialTheme {
        NasConnectionEditorForm(
            uiState = NasEditorUiState(
                nickname = "My NAS",
                hostname = "192.168.1.100",
                path = "music",
                username = "user"
            ),
            onNicknameChange = {},
            onHostnameChange = {},
            onPathChange = {},
            onUsernameChange = {},
            onPasswordChange = {},
            onTestConnectionClick = {},
            onSaveClick = {}
        )
    }
}
