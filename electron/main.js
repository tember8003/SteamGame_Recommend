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
console.log('Working directory:', process.cwd());

// 올바른 앱 루트 경로 얻기 (asar 문제 해결)
const getAppRootPath = () => {
    if (app.isPackaged) {
        return process.resourcesPath;
    } else {
        return process.cwd(); // 개발 환경에서는 process.cwd() 사용
    }
};

console.log('App root path:', getAppRootPath());

// API 키 파일 경로
const getApiKeysPath = () => {
    const userDataPath = app.getPath('userData');
    return path.join(userDataPath, 'api-keys.json');
};

// API 키 로드
function loadApiKeys() {
    try {
        const apiKeysPath = getApiKeysPath();
        console.log('Looking for API keys at:', apiKeysPath);
        if (fs.existsSync(apiKeysPath)) {
            const data = fs.readFileSync(apiKeysPath, 'utf8');
            apiKeys = JSON.parse(data);
            console.log('✅ API keys loaded');
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
        console.log('✅ API keys saved');
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
    try {
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
                            if (mainWindow) mainWindow.reload();
                        }
                    },
                    {
                        label: 'Toggle Developer Tools',
                        accelerator: process.platform === 'darwin' ? 'Alt+Cmd+I' : 'Ctrl+Shift+I',
                        click() {
                            if (mainWindow) mainWindow.webContents.toggleDevTools();
                        }
                    }
                ]
            },
            {
                label: 'Help',
                submenu: [
                    {
                        label: 'About',
                        click() {
                            showAboutDialog();
                        }
                    }
                ]
            }
        ];

        const menu = Menu.buildFromTemplate(template);
        Menu.setApplicationMenu(menu);
        console.log('✅ Application menu created');
    } catch (error) {
        console.error('❌ Failed to create application menu:', error);
    }
}

// About 다이얼로그 표시
function showAboutDialog() {
    try {
        const aboutHtml = `
            <div style="padding: 40px; font-family: Arial; text-align: center; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; min-height: 100vh; display: flex; align-items: center; justify-content: center;">
                <div style="background: rgba(255,255,255,0.1); padding: 40px; border-radius: 15px; backdrop-filter: blur(10px); max-width: 500px;">
                    <h1 style="margin-bottom: 20px;">🎮 Steam Game Recommend</h1>
                    <p style="font-size: 18px; margin-bottom: 15px;">Version 1.0.0</p>
                    <p style="margin-bottom: 20px;">AI-powered Steam game recommendation system</p>
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
    } catch (error) {
        console.error('❌ Failed to show about dialog:', error);
    }
}

// IPC 핸들러들
ipcMain.handle('get-api-keys', () => {
    console.log('IPC: get-api-keys called');
    return apiKeys;
});

ipcMain.handle('save-api-keys', (event, keys) => {
    console.log('IPC: save-api-keys called');
    return saveApiKeys(keys);
});

ipcMain.handle('validate-steam-api', async (event, apiKey) => {
    console.log('IPC: validate-steam-api called');
    return await validateSteamApiKey(apiKey);
});

ipcMain.handle('validate-gemini-api', async (event, apiKey) => {
    console.log('IPC: validate-gemini-api called');
    return await validateGeminiApiKey(apiKey);
});

ipcMain.handle('start-main-app', () => {
    console.log('IPC: start-main-app called');
    startMainApp();
});

// 메인 앱 시작
function startMainApp() {
    console.log('🚀 Starting main application...');
    try {
        // 로딩 화면 표시
        showLoadingScreen();

        // Java 앱 시작
        setTimeout(() => {
            startJavaApp();
        }, 1000);
    } catch (error) {
        console.error('❌ Error in startMainApp:', error);
        showErrorScreen('Failed to start main application: ' + error.message);
    }
}

// 로딩 화면 표시
function showLoadingScreen() {
    try {
        const appRoot = getAppRootPath();
        const loadingPath = path.join(appRoot, 'html', 'loading.html');
        console.log('Looking for loading.html at:', loadingPath);

        if (fs.existsSync(loadingPath)) {
            console.log('✅ Loading loading.html from file');
            mainWindow.loadFile(loadingPath);
        } else {
            console.log('ℹ️ loading.html not found, using inline loading screen');
            const loadingHtml = `
                <div style="text-align:center;padding:50px;font-family:Arial;background:#1a1a2e;color:white;min-height:100vh;display:flex;flex-direction:column;justify-content:center;align-items:center;">
                    <h2 style="color:#00d4ff;margin-bottom:20px;">🎮 Steam Game Recommend</h2>
                    <p style="margin-bottom:30px;">서버를 시작하고 있습니다...</p>
                    <div style="border:4px solid #333;border-top:4px solid #00d4ff;border-radius:50%;width:40px;height:40px;animation:spin 2s linear infinite;margin:20px auto;"></div>
                    <style>@keyframes spin{0%{transform:rotate(0deg)}100%{transform:rotate(360deg)}}</style>
                </div>
            `;
            mainWindow.loadURL(`data:text/html,${encodeURIComponent(loadingHtml)}`);
        }
    } catch (error) {
        console.error('❌ Error in showLoadingScreen:', error);
        showErrorScreen('Failed to show loading screen: ' + error.message);
    }
}

// 설정 화면 표시
function showConfigScreen() {
    try {
        const appRoot = getAppRootPath();
        const configPath = path.join(appRoot, 'html', 'config.html');
        console.log('Looking for config.html at:', configPath);

        if (fs.existsSync(configPath)) {
            console.log('✅ Loading config.html from file');
            mainWindow.loadFile(configPath);
        } else {
            console.log('ℹ️ config.html not found, using inline config screen');
            const configHtml = `
                <div style="padding: 40px; font-family: Arial; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; min-height: 100vh; display: flex; align-items: center; justify-content: center;">
                    <div style="background: rgba(255,255,255,0.1); padding: 40px; border-radius: 15px; backdrop-filter: blur(10px); width: 600px; max-width: 90%;">
                        <h2 style="text-align: center; margin-bottom: 30px;">🔑 API Key Configuration</h2>
                        
                        <div style="margin-bottom: 25px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600;">Steam API Key:</label>
                            <input type="text" id="steamApiKey" placeholder="Enter your Steam API key" style="width: 100%; padding: 12px; border: none; border-radius: 8px; background: rgba(255,255,255,0.9); color: #333; font-size: 14px; box-sizing: border-box;">
                            <div style="font-size: 12px; color: rgba(255,255,255,0.8); margin-top: 5px;">
                                Get your Steam API key from: steamcommunity.com/dev/apikey
                            </div>
                        </div>

                        <div style="margin-bottom: 25px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600;">Gemini API Key:</label>
                            <input type="text" id="geminiApiKey" placeholder="Enter your Gemini API key" style="width: 100%; padding: 12px; border: none; border-radius: 8px; background: rgba(255,255,255,0.9); color: #333; font-size: 14px; box-sizing: border-box;">
                            <div style="font-size: 12px; color: rgba(255,255,255,0.8); margin-top: 5px;">
                                Get your Gemini API key from: aistudio.google.com/app/apikey
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
                    const { ipcRenderer } = require('electron');
                    
                    // 기존 API 키 로드
                    window.addEventListener('DOMContentLoaded', () => {
                        ipcRenderer.invoke('get-api-keys').then(keys => {
                            if (keys.steamApiKey) document.getElementById('steamApiKey').value = keys.steamApiKey;
                            if (keys.geminiApiKey) document.getElementById('geminiApiKey').value = keys.geminiApiKey;
                        }).catch(err => console.error('Failed to load API keys:', err));
                    });
                    
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
    } catch (error) {
        console.error('❌ Error in showConfigScreen:', error);
        showErrorScreen('Failed to show config screen: ' + error.message);
    }
}

// Java 앱 시작 - 수정된 경로 처리
function startJavaApp() {
    console.log('=== Starting Java App ===');

    try {
        const appRoot = getAppRootPath();

        // 올바른 JAR 파일 경로들
        const possiblePaths = [
            // 빌드된 앱에서의 경로
            path.join(process.resourcesPath, 'recommend-0.0.1-SNAPSHOT.jar'),

            // 개발 환경에서의 경로 (올바른 경로 사용)
            path.join(appRoot, 'resources', 'recommend-0.0.1-SNAPSHOT.jar'),
            path.join(appRoot, '..', 'build', 'libs', 'recommend-0.0.1-SNAPSHOT.jar')
        ];

        let jarPath = null;
        console.log('Searching for JAR file...');
        for (const testPath of possiblePaths) {
            console.log(`  Checking: ${testPath}`);
            if (fs.existsSync(testPath)) {
                jarPath = testPath;
                console.log(`  ✅ Found JAR at: ${jarPath}`);
                break;
            } else {
                console.log(`  ❌ Not found`);
            }
        }

        if (!jarPath) {
            console.error('❌ JAR file not found');
            showErrorScreen('JAR file not found: recommend-0.0.1-SNAPSHOT.jar<br><br>Please ensure the JAR file is in the resources folder.');
            return;
        }

        // 환경변수에 API 키 설정
        const env = {
            ...process.env,
            STEAM_API_KEY: apiKeys.steamApiKey || '',
            GEMINI_API_KEY: apiKeys.geminiApiKey || '',
            SPRING_PROFILES_ACTIVE: 'h2'
        };

        // 작업 디렉토리를 recommend 폴더로 설정 (data 폴더가 있는 곳)
        const workingDirectory = path.join(appRoot, '..');  // recommend 폴더

        console.log('🚀 Starting Java process...');
        console.log('JAR path:', jarPath);
        console.log('Working directory:', workingDirectory);

        // data 폴더 존재 확인
        const dataFolder = path.join(workingDirectory, 'data');
        const initSqlPath = path.join(dataFolder, 'init.sql');

        if (fs.existsSync(initSqlPath)) {
            console.log('✅ Found init.sql at:', initSqlPath);
        } else {
            console.log('⚠️ init.sql not found at:', initSqlPath);
        }

        // 기존 프로세스가 있다면 종료
        if (javaProcess) {
            console.log('Terminating existing Java process...');
            javaProcess.kill();
        }

        javaProcess = spawn('java', ['-jar', jarPath, '--spring.profiles.active=h2'], {
            stdio: ['ignore', 'pipe', 'pipe'],
            cwd: workingDirectory,  // recommend 폴더에서 실행
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
            const errorOutput = data.toString();
            console.error('Java stderr:', errorOutput);
        });

        javaProcess.on('close', (code) => {
            console.log(`Java process terminated with code: ${code}`);
            isJavaServerReady = false;

            if (code !== 0 && code !== null) {
                console.error(`Java process exited with error code: ${code}`);
                showErrorScreen(`Java application exited with error code: ${code}`);
            }
        });

        javaProcess.on('error', (error) => {
            console.error('Failed to start Java process:', error);
            isJavaServerReady = false;

            if (error.code === 'ENOENT') {
                showErrorScreen('Java is not installed or not found in PATH.<br><br>Please install Java and try again.');
            } else {
                showErrorScreen('Failed to start Java process: ' + error.message);
            }
        });

    } catch (error) {
        console.error('Error in startJavaApp:', error);
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

            const response = await fetch('http://localhost:8888/index.html', {
                method: 'GET',
                signal: AbortSignal.timeout(3000)
            });

            if (response.ok) {
                console.log('✅ Server connection successful!');
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
    showErrorScreen('Failed to connect to server after maximum attempts.');
}

// 에러 화면 표시
function showErrorScreen(message) {
    try {
        const errorHtml = `
            <div style="padding: 40px; font-family: Arial; text-align: center; background: #f44336; color: white; min-height: 100vh; display: flex; align-items: center; justify-content: center;">
                <div style="max-width: 600px;">
                    <h1>❌ Error</h1>
                    <p style="font-size: 18px; margin: 20px 0; line-height: 1.6;">${message}</p>
                    <div style="margin-top: 30px;">
                        <button onclick="location.reload()" style="padding: 12px 24px; font-size: 16px; background: white; color: #f44336; border: none; border-radius: 8px; cursor: pointer; font-weight: 600; margin-right: 10px;">Retry</button>
                        <button onclick="window.close()" style="padding: 12px 24px; font-size: 16px; background: rgba(255,255,255,0.2); color: white; border: 1px solid rgba(255,255,255,0.3); border-radius: 8px; cursor: pointer; font-weight: 600;">Close</button>
                    </div>
                </div>
            </div>
        `;
        if (mainWindow && !mainWindow.isDestroyed()) {
            mainWindow.loadURL(`data:text/html,${encodeURIComponent(errorHtml)}`);
        }
    } catch (error) {
        console.error('❌ Error in showErrorScreen:', error);
    }
}

function createWindow() {
    console.log('=== Creating Window ===');

    try {
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

            // 개발 중에는 DevTools 열기
            if (!app.isPackaged) {
                mainWindow.webContents.openDevTools();
            }
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

    } catch (error) {
        console.error('❌ Error creating window:', error);
        app.quit();
    }
}

// 앱 이벤트 핸들러
app.whenReady().then(() => {
    console.log('=== App Ready ===');
    createWindow();
}).catch(error => {
    console.error('❌ App failed to become ready:', error);
});

app.on('window-all-closed', () => {
    console.log('=== All Windows Closed ===');
    // Java 프로세스 종료
    if (javaProcess) {
        console.log('Terminating Java process...');
        javaProcess.kill();
    }

    if (process.platform !== 'darwin') {
        app.quit();
    }
});

app.on('activate', () => {
    console.log('=== App Activated ===');
    if (BrowserWindow.getAllWindows().length === 0) {
        createWindow();
    }
});

// 앱 종료 시 Java 프로세스 정리
app.on('before-quit', () => {
    console.log('=== App Before Quit ===');
    if (javaProcess) {
        console.log('Cleaning up Java process before quit...');
        javaProcess.kill();
    }
});

console.log('=== Fixed Main.js loaded successfully ===');