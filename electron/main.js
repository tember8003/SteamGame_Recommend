const { app, BrowserWindow, ipcMain, shell, Menu } = require('electron');
const { spawn } = require('child_process');
const path = require('path');
const fs = require('fs');

let mainWindow;
let javaProcess;
let isJavaServerReady = false;
let apiKeys = {
    steamApiKey: '',
    geminiApiKey: ''
};

console.log('=== App Starting ===');
console.log('App path:', app.getAppPath());
console.log('Is packaged:', app.isPackaged);

// API 키 파일 경로
const getApiKeysPath = () => {
    const userDataPath = app.getPath('userData');
    return path.join(userDataPath, 'api-keys.json');
};

// API 키 로드
function loadApiKeys() {
    try {
        const apiKeysPath = getApiKeysPath();
        if (fs.existsSync(apiKeysPath)) {
            const data = fs.readFileSync(apiKeysPath, 'utf8');
            apiKeys = JSON.parse(data);
            console.log('✅ API keys loaded from:', apiKeysPath);
            return true;
        } else {
            console.log('ℹ️ No API keys file found');
            return false;
        }
    } catch (error) {
        console.error('❌ Failed to load API keys:', error);
        return false;
    }
}

// API 키 저장
function saveApiKeys(keys) {
    try {
        const apiKeysPath = getApiKeysPath();
        const userDataPath = path.dirname(apiKeysPath);

        if (!fs.existsSync(userDataPath)) {
            fs.mkdirSync(userDataPath, { recursive: true });
        }

        apiKeys = { ...apiKeys, ...keys };
        fs.writeFileSync(apiKeysPath, JSON.stringify(apiKeys, null, 2));
        console.log('✅ API keys saved to:', apiKeysPath);
        return true;
    } catch (error) {
        console.error('❌ Failed to save API keys:', error);
        throw error;
    }
}

// Steam API 키 유효성 검증
async function validateSteamApiKey(apiKey) {
    if (!apiKey || apiKey.length !== 32) {
        return { valid: false, message: 'Steam API key must be 32 characters long' };
    }

    try {
        const response = await fetch(`http://api.steampowered.com/ISteamUser/GetPlayerSummaries/v0002/?key=${apiKey}&steamids=76561197960435530`, {
            signal: AbortSignal.timeout(5000)
        });

        if (response.ok) {
            const data = await response.json();
            if (data.response && data.response.players) {
                return { valid: true, message: 'Steam API key is valid' };
            }
        }

        return { valid: false, message: 'Invalid Steam API key or API error' };
    } catch (error) {
        console.error('Steam API validation error:', error);
        return { valid: false, message: 'Failed to validate Steam API key (network error)' };
    }
}

// Gemini API 키 유효성 검증
async function validateGeminiApiKey(apiKey) {
    if (!apiKey || apiKey.length < 20) {
        return { valid: false, message: 'Gemini API key appears to be too short' };
    }

    try {
        const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models?key=${apiKey}`, {
            signal: AbortSignal.timeout(5000)
        });

        if (response.ok) {
            return { valid: true, message: 'Gemini API key is valid' };
        } else if (response.status === 403) {
            return { valid: false, message: 'Invalid Gemini API key or access denied' };
        } else {
            return { valid: false, message: 'Failed to validate Gemini API key' };
        }
    } catch (error) {
        console.error('Gemini API validation error:', error);
        return { valid: false, message: 'Failed to validate Gemini API key (network error)' };
    }
}

// 앱 메뉴 생성
function createApplicationMenu() {
    const template = [
        {
            label: 'File',
            submenu: [
                {
                    label: 'Settings',
                    accelerator: 'CmdOrCtrl+,',
                    click() {
                        showConfigScreen();
                    }
                },
                {
                    type: 'separator'
                },
                {
                    label: 'Restart App',
                    accelerator: 'CmdOrCtrl+R',
                    click() {
                        app.relaunch();
                        app.exit();
                    }
                },
                {
                    type: 'separator'
                },
                {
                    label: 'Exit',
                    accelerator: process.platform === 'darwin' ? 'Cmd+Q' : 'Ctrl+Q',
                    click() {
                        app.quit();
                    }
                }
            ]
        },
        {
            label: 'View',
            submenu: [
                {
                    label: 'Reload',
                    accelerator: 'CmdOrCtrl+Shift+R',
                    click() {
                        mainWindow.reload();
                    }
                },
                {
                    label: 'Force Reload',
                    accelerator: 'CmdOrCtrl+Shift+F5',
                    click() {
                        mainWindow.webContents.reloadIgnoringCache();
                    }
                },
                {
                    label: 'Toggle Developer Tools',
                    accelerator: process.platform === 'darwin' ? 'Alt+Cmd+I' : 'Ctrl+Shift+I',
                    click() {
                        mainWindow.webContents.toggleDevTools();
                    }
                },
                {
                    type: 'separator'
                },
                {
                    label: 'Actual Size',
                    accelerator: 'CmdOrCtrl+0',
                    click() {
                        mainWindow.webContents.setZoomLevel(0);
                    }
                },
                {
                    label: 'Zoom In',
                    accelerator: 'CmdOrCtrl+Plus',
                    click() {
                        const currentZoom = mainWindow.webContents.getZoomLevel();
                        mainWindow.webContents.setZoomLevel(currentZoom + 0.5);
                    }
                },
                {
                    label: 'Zoom Out',
                    accelerator: 'CmdOrCtrl+-',
                    click() {
                        const currentZoom = mainWindow.webContents.getZoomLevel();
                        mainWindow.webContents.setZoomLevel(currentZoom - 0.5);
                    }
                }
            ]
        },
        {
            label: 'Help',
            submenu: [
                {
                    label: 'Steam API Key Guide',
                    click() {
                        shell.openExternal('https://steamcommunity.com/dev/apikey');
                    }
                },
                {
                    label: 'Gemini API Key Guide',
                    click() {
                        shell.openExternal('https://aistudio.google.com/app/apikey');
                    }
                },
                {
                    type: 'separator'
                },
                {
                    label: 'About',
                    click() {
                        showAboutDialog();
                    }
                }
            ]
        }
    ];

    // macOS 메뉴 조정
    if (process.platform === 'darwin') {
        template.unshift({
            label: app.getName(),
            submenu: [
                {
                    label: 'About ' + app.getName(),
                    click() {
                        showAboutDialog();
                    }
                },
                {
                    type: 'separator'
                },
                {
                    label: 'Settings',
                    accelerator: 'Cmd+,',
                    click() {
                        showConfigScreen();
                    }
                },
                {
                    type: 'separator'
                },
                {
                    label: 'Hide ' + app.getName(),
                    accelerator: 'Cmd+H',
                    role: 'hide'
                },
                {
                    label: 'Hide Others',
                    accelerator: 'Cmd+Shift+H',
                    role: 'hideothers'
                },
                {
                    label: 'Show All',
                    role: 'unhide'
                },
                {
                    type: 'separator'
                },
                {
                    label: 'Quit',
                    accelerator: 'Cmd+Q',
                    click() {
                        app.quit();
                    }
                }
            ]
        });
    }

    const menu = Menu.buildFromTemplate(template);
    Menu.setApplicationMenu(menu);
}

// About 다이얼로그 표시
function showAboutDialog() {
    const aboutHtml = `
        <div style="padding: 40px; font-family: Arial; text-align: center; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; min-height: 100vh; display: flex; align-items: center; justify-content: center;">
            <div style="background: rgba(255,255,255,0.1); padding: 40px; border-radius: 15px; backdrop-filter: blur(10px); max-width: 500px;">
                <h1 style="margin-bottom: 20px;">🎮 Steam Game Recommend</h1>
                <p style="font-size: 18px; margin-bottom: 15px;">Version 1.0.0</p>
                <p style="margin-bottom: 20px;">AI-powered Steam game recommendation system</p>
                <div style="margin-bottom: 20px;">
                    <p style="font-size: 14px; color: rgba(255,255,255,0.8);">
                        Built with Spring Boot, Electron, and Google Gemini AI<br>
                        Find your next favorite game with personalized recommendations
                    </p>
                </div>
                <button onclick="window.close()" style="padding: 10px 20px; background: #4CAF50; color: white; border: none; border-radius: 5px; cursor: pointer; font-weight: 600;">Close</button>
            </div>
        </div>
    `;

    const aboutWindow = new BrowserWindow({
        width: 600,
        height: 500,
        modal: true,
        parent: mainWindow,
        webPreferences: {
            nodeIntegration: true,
            contextIsolation: false
        }
    });

    aboutWindow.loadURL(`data:text/html,${encodeURIComponent(aboutHtml)}`);
    aboutWindow.setMenuBarVisibility(false);
}

// IPC 핸들러들
ipcMain.handle('get-api-keys', () => {
    return apiKeys;
});

ipcMain.handle('save-api-keys', (event, keys) => {
    return saveApiKeys(keys);
});

ipcMain.handle('validate-steam-api', async (event, apiKey) => {
    return await validateSteamApiKey(apiKey);
});

ipcMain.handle('validate-gemini-api', async (event, apiKey) => {
    return await validateGeminiApiKey(apiKey);
});

ipcMain.handle('start-main-app', () => {
    startMainApp();
});

// 메인 앱 시작
function startMainApp() {
    console.log('🚀 Starting main application...');

    // 로딩 화면 표시
    showLoadingScreen();

    // Java 앱 시작
    setTimeout(() => {
        startJavaApp();
    }, 1000);
}

// 로딩 화면 표시
function showLoadingScreen() {
    const loadingPath = path.join(__dirname, 'html/loading.html');
    if (fs.existsSync(loadingPath)) {
        mainWindow.loadFile(loadingPath);
    } else {
        // loading.html이 없으면 간단한 로딩 화면
        mainWindow.loadURL('data:text/html,<div style="text-align:center;padding:50px;font-family:Arial;"><h2>🎮 Steam Game Recommend</h2><p>서버를 시작하고 있습니다...</p><div style="border:4px solid #f3f3f3;border-top:4px solid #3498db;border-radius:50%;width:40px;height:40px;animation:spin 2s linear infinite;margin:20px auto;"></div><style>@keyframes spin{0%{transform:rotate(0deg)}100%{transform:rotate(360deg)}}</style></div>');
    }
}

// 설정 화면 표시 (API 키 검증 기능 포함)
function showConfigScreen() {
    const configPath = path.join(__dirname, 'html/config.html');
    if (fs.existsSync(configPath)) {
        mainWindow.loadFile(configPath);
    } else {
        // config.html이 없으면 향상된 설정 화면
        const configHtml = `
            <div style="padding: 40px; font-family: Arial; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; min-height: 100vh; display: flex; align-items: center; justify-content: center;">
                <div style="background: rgba(255,255,255,0.1); padding: 40px; border-radius: 15px; backdrop-filter: blur(10px); width: 600px; max-width: 90%;">
                    <h2 style="text-align: center; margin-bottom: 30px;">🔑 API Key Configuration</h2>
                    
                    <div style="margin-bottom: 25px;">
                        <label style="display: block; margin-bottom: 8px; font-weight: 600;">Steam API Key:</label>
                        <div style="position: relative;">
                            <input type="text" id="steamApiKey" placeholder="Enter your Steam API key" style="width: 100%; padding: 12px 40px 12px 12px; border: none; border-radius: 8px; background: rgba(255,255,255,0.9); color: #333; font-size: 14px; box-sizing: border-box;">
                            <button id="validateSteam" onclick="validateSteamKey()" style="position: absolute; right: 5px; top: 5px; padding: 7px 12px; background: #2196F3; color: white; border: none; border-radius: 5px; cursor: pointer; font-size: 12px;">Verify</button>
                        </div>
                        <div id="steamStatus" style="font-size: 12px; margin-top: 5px; min-height: 18px;"></div>
                        <div style="font-size: 12px; color: rgba(255,255,255,0.8); margin-top: 5px;">
                            Get your Steam API key from: 
                            <a href="#" onclick="openExternal('https://steamcommunity.com/dev/apikey')" style="color: #add8e6; text-decoration: none;">steamcommunity.com/dev/apikey</a>
                        </div>
                    </div>

                    <div style="margin-bottom: 25px;">
                        <label style="display: block; margin-bottom: 8px; font-weight: 600;">Gemini API Key:</label>
                        <div style="position: relative;">
                            <input type="text" id="geminiApiKey" placeholder="Enter your Gemini API key" style="width: 100%; padding: 12px 40px 12px 12px; border: none; border-radius: 8px; background: rgba(255,255,255,0.9); color: #333; font-size: 14px; box-sizing: border-box;">
                            <button id="validateGemini" onclick="validateGeminiKey()" style="position: absolute; right: 5px; top: 5px; padding: 7px 12px; background: #2196F3; color: white; border: none; border-radius: 5px; cursor: pointer; font-size: 12px;">Verify</button>
                        </div>
                        <div id="geminiStatus" style="font-size: 12px; margin-top: 5px; min-height: 18px;"></div>
                        <div style="font-size: 12px; color: rgba(255,255,255,0.8); margin-top: 5px;">
                            Get your Gemini API key from: 
                            <a href="#" onclick="openExternal('https://aistudio.google.com/app/apikey')" style="color: #add8e6; text-decoration: none;">aistudio.google.com/app/apikey</a>
                        </div>
                    </div>

                    <div style="display: flex; gap: 10px; margin-top: 30px;">
                        <button onclick="skipConfig()" style="flex: 1; padding: 12px 20px; border: none; border-radius: 8px; font-size: 16px; font-weight: 600; cursor: pointer; background: rgba(255,255,255,0.2); color: white; border: 1px solid rgba(255,255,255,0.3);">Skip for Now</button>
                        <button onclick="saveConfig()" style="flex: 1; padding: 12px 20px; border: none; border-radius: 8px; font-size: 16px; font-weight: 600; cursor: pointer; background: #4CAF50; color: white;">Save & Continue</button>
                    </div>
                    <div id="status" style="margin-top: 15px; padding: 10px; border-radius: 5px; text-align: center; font-weight: 600; display: none;"></div>
                </div>
            </div>
            <script>
                const { ipcRenderer, shell } = require('electron');
                
                // 외부 링크 열기
                function openExternal(url) {
                    shell.openExternal(url);
                }
                
                // 기존 API 키 로드
                window.addEventListener('DOMContentLoaded', () => {
                    ipcRenderer.invoke('get-api-keys').then(keys => {
                        if (keys.steamApiKey) document.getElementById('steamApiKey').value = keys.steamApiKey;
                        if (keys.geminiApiKey) document.getElementById('geminiApiKey').value = keys.geminiApiKey;
                    });
                });
                
                // Steam API 키 검증
                async function validateSteamKey() {
                    const apiKey = document.getElementById('steamApiKey').value.trim();
                    const statusDiv = document.getElementById('steamStatus');
                    const validateBtn = document.getElementById('validateSteam');
                    
                    if (!apiKey) {
                        statusDiv.style.color = '#ff6b6b';
                        statusDiv.textContent = 'Please enter a Steam API key';
                        return;
                    }
                    
                    validateBtn.textContent = 'Checking...';
                    validateBtn.disabled = true;
                    statusDiv.style.color = '#ffa726';
                    statusDiv.textContent = 'Validating Steam API key...';
                    
                    try {
                        const result = await ipcRenderer.invoke('validate-steam-api', apiKey);
                        
                        if (result.valid) {
                            statusDiv.style.color = '#4caf50';
                            statusDiv.textContent = '✅ ' + result.message;
                        } else {
                            statusDiv.style.color = '#ff6b6b';
                            statusDiv.textContent = '❌ ' + result.message;
                        }
                    } catch (error) {
                        statusDiv.style.color = '#ff6b6b';
                        statusDiv.textContent = '❌ Validation failed: ' + error.message;
                    }
                    
                    validateBtn.textContent = 'Verify';
                    validateBtn.disabled = false;
                }
                
                // Gemini API 키 검증
                async function validateGeminiKey() {
                    const apiKey = document.getElementById('geminiApiKey').value.trim();
                    const statusDiv = document.getElementById('geminiStatus');
                    const validateBtn = document.getElementById('validateGemini');
                    
                    if (!apiKey) {
                        statusDiv.style.color = '#ff6b6b';
                        statusDiv.textContent = 'Please enter a Gemini API key';
                        return;
                    }
                    
                    validateBtn.textContent = 'Checking...';
                    validateBtn.disabled = true;
                    statusDiv.style.color = '#ffa726';
                    statusDiv.textContent = 'Validating Gemini API key...';
                    
                    try {
                        const result = await ipcRenderer.invoke('validate-gemini-api', apiKey);
                        
                        if (result.valid) {
                            statusDiv.style.color = '#4caf50';
                            statusDiv.textContent = '✅ ' + result.message;
                        } else {
                            statusDiv.style.color = '#ff6b6b';
                            statusDiv.textContent = '❌ ' + result.message;
                        }
                    } catch (error) {
                        statusDiv.style.color = '#ff6b6b';
                        statusDiv.textContent = '❌ Validation failed: ' + error.message;
                    }
                    
                    validateBtn.textContent = 'Verify';
                    validateBtn.disabled = false;
                }
                
                async function saveConfig() {
                    const steamApiKey = document.getElementById('steamApiKey').value.trim();
                    const geminiApiKey = document.getElementById('geminiApiKey').value.trim();
                    const statusDiv = document.getElementById('status');
                    
                    try {
                        await ipcRenderer.invoke('save-api-keys', { steamApiKey, geminiApiKey });
                        statusDiv.style.display = 'block';
                        statusDiv.style.background = 'rgba(76, 175, 80, 0.2)';
                        statusDiv.style.border = '1px solid #4CAF50';
                        statusDiv.textContent = '✅ API keys saved successfully!';
                        setTimeout(() => { ipcRenderer.invoke('start-main-app'); }, 1500);
                    } catch (error) {
                        statusDiv.style.display = 'block';
                        statusDiv.style.background = 'rgba(244, 67, 54, 0.2)';
                        statusDiv.style.border = '1px solid #f44336';
                        statusDiv.textContent = '❌ Failed to save API keys: ' + error.message;
                    }
                }
                
                function skipConfig() {
                    ipcRenderer.invoke('start-main-app');
                }
            </script>
        `;
        mainWindow.loadURL(`data:text/html,${encodeURIComponent(configHtml)}`);
    }
}

// Java 앱 시작
function startJavaApp() {
    console.log('=== Starting Java App ===');

    const possiblePaths = [
        path.join(process.resourcesPath, 'recommend-0.0.1-SNAPSHOT.jar'),
        path.join(__dirname, 'resources', 'recommend-0.0.1-SNAPSHOT.jar'),
        path.join(process.cwd(), 'resources', 'recommend-0.0.1-SNAPSHOT.jar'),
        path.join(__dirname, '..', 'resources', 'recommend-0.0.1-SNAPSHOT.jar'),
        path.join(app.getAppPath(), 'resources', 'recommend-0.0.1-SNAPSHOT.jar')
    ];

    let jarPath = null;
    for (const testPath of possiblePaths) {
        if (fs.existsSync(testPath)) {
            jarPath = testPath;
            console.log(`✅ Found JAR at: ${jarPath}`);
            break;
        }
    }

    if (!jarPath) {
        console.error('❌ JAR file not found');
        showErrorScreen('JAR file not found: recommend-0.0.1-SNAPSHOT.jar');
        return;
    }

    // 환경변수에 API 키 설정
    const env = {
        ...process.env,
        STEAM_API_KEY: apiKeys.steamApiKey || '',
        GEMINI_API_KEY: apiKeys.geminiApiKey || '',
        SPRING_PROFILES_ACTIVE: 'release'
    };

    console.log('🚀 Starting Java process with API keys...');

    try {
        // 기존 프로세스가 있다면 종료
        if (javaProcess) {
            javaProcess.kill();
        }

        javaProcess = spawn('java', ['-jar', jarPath,'--spring.profiles.active=h2'], {
            stdio: ['ignore', 'pipe', 'pipe'],
            cwd: path.dirname(jarPath),
            env: env
        });

        javaProcess.stdout.on('data', (data) => {
            const output = data.toString();
            console.log('Java stdout:', output);

            if (output.includes('Started RecommendApplication') ||
                output.includes('Tomcat started on port 8888')) {
                console.log('✅ Java server started successfully!');
                isJavaServerReady = true;

                // 서버 준비되면 연결 시도
                setTimeout(() => {
                    checkServerAndConnect();
                }, 2000);
            }
        });

        javaProcess.stderr.on('data', (data) => {
            console.error('Java stderr:', data.toString());
        });

        javaProcess.on('close', (code) => {
            console.log(`Java process terminated with code: ${code}`);
            isJavaServerReady = false;
        });

        javaProcess.on('error', (error) => {
            console.error('Failed to start Java process:', error);
            isJavaServerReady = false;
            showErrorScreen('Failed to start Java process: ' + error.message);
        });

    } catch (error) {
        console.error('Error starting Java process:', error);
        showErrorScreen('Error starting Java process: ' + error.message);
    }
}

// 서버 연결 확인 및 index.html 로드
async function checkServerAndConnect() {
    console.log('=== Checking Server Connection ===');

    const maxAttempts = 15;
    let attempts = 0;

    while (attempts < maxAttempts) {
        try {
            console.log(`🔍 Connection attempt ${attempts + 1}/${maxAttempts}...`);

            // index.html 연결 테스트
            const response = await fetch('http://localhost:8888/index.html', {
                method: 'GET',
                signal: AbortSignal.timeout(3000)
            });

            if (response.ok) {
                console.log('✅ Server connection successful!');
                // 성공하면 index.html 로드
                mainWindow.loadURL('http://localhost:8888/index.html');
                return;
            }
        } catch (error) {
            console.log(`⏳ Server not ready yet: ${error.message}`);
        }

        await new Promise(resolve => setTimeout(resolve, 1000));
        attempts++;
    }

    console.log('❌ Server connection failed');
    showErrorScreen('Failed to connect to server after maximum attempts');
}

// 에러 화면 표시
function showErrorScreen(message) {
    const errorHtml = `
        <div style="padding: 40px; font-family: Arial; text-align: center; background: #f44336; color: white; min-height: 100vh; display: flex; align-items: center; justify-content: center;">
            <div>
                <h1>❌ Error</h1>
                <p style="font-size: 18px; margin: 20px 0;">${message}</p>
                <div style="margin-top: 20px;">
                    <button onclick="location.reload()" style="padding: 12px 24px; font-size: 16px; background: white; color: #f44336; border: none; border-radius: 8px; cursor: pointer; font-weight: 600; margin-right: 10px;">Retry</button>
                    <button onclick="showSettings()" style="padding: 12px 24px; font-size: 16px; background: rgba(255,255,255,0.2); color: white; border: 1px solid rgba(255,255,255,0.3); border-radius: 8px; cursor: pointer; font-weight: 600;">Settings</button>
                </div>
            </div>
        </div>
        <script>
            const { ipcRenderer } = require('electron');
            function showSettings() {
                ipcRenderer.invoke('show-config-screen');
            }
        </script>
    `;
    mainWindow.loadURL(`data:text/html,${encodeURIComponent(errorHtml)}`);
}

function createWindow() {
    console.log('=== Creating Window ===');

    mainWindow = new BrowserWindow({
        width: 1200,
        height: 800,
        show: false,
        webPreferences: {
            nodeIntegration: true,
            contextIsolation: false
        }
    });

    // 애플리케이션 메뉴 생성
    createApplicationMenu();

    mainWindow.once('ready-to-show', () => {
        console.log('✅ Window ready - showing now');
        mainWindow.show();
        mainWindow.focus();
        // 개발 중에만 DevTools 열기 (릴리즈시 주석 처리)
        // mainWindow.webContents.openDevTools();
    });

    // API 키 로드 시도
    const hasApiKeys = loadApiKeys();

    // API 키가 있고 유효하면 메인 앱 시작, 없으면 설정 화면
    if (hasApiKeys && apiKeys.steamApiKey && apiKeys.geminiApiKey) {
        console.log('✅ API keys found - starting main app');
        startMainApp();
    } else {
        console.log('⚙️ API keys missing or incomplete - showing config screen');
        showConfigScreen();
    }
}