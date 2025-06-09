const { app, BrowserWindow, shell, ipcMain } = require('electron');
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
        }
    } catch (error) {
        console.error('❌ Failed to load API keys:', error);
    }
}

// API 키 저장
function saveApiKeys(keys) {
    try {
        const apiKeysPath = getApiKeysPath();
        const userDataPath = path.dirname(apiKeysPath);

        // userData 폴더가 없으면 생성
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

// IPC 핸들러
ipcMain.handle('get-api-keys', () => {
    return apiKeys;
});

ipcMain.handle('save-api-keys', (event, keys) => {
    return saveApiKeys(keys);
});

ipcMain.handle('start-main-app', () => {
    showLoadingScreen();
    startJavaApp();
    setTimeout(() => {
        checkServerAndConnect();
    }, 3000);
});

function createWindow() {
    console.log('=== Creating Window ===');

    mainWindow = new BrowserWindow({
        width: 1200,
        height: 800,
        webPreferences: {
            nodeIntegration: true,
            contextIsolation: false
        },
        show: false
    });

    // API 키가 없으면 설정 화면 표시
    loadApiKeys();

    if (!apiKeys.steamApiKey || !apiKeys.geminiApiKey) {
        console.log('⚙️ Showing API key configuration');
        mainWindow.loadFile(path.join(__dirname, 'html', 'config.html'));
    } else {
        console.log('✅ API keys found, starting app');
        showLoadingScreen();
        startJavaApp();
        setTimeout(() => {
            checkServerAndConnect();
        }, 3000);
    }

    mainWindow.show();
    console.log('✅ Window created and shown');

    mainWindow.on('closed', () => {
        mainWindow = null;
    });
}

function showLoadingScreen() {
    console.log('📱 Showing loading screen');
    mainWindow.loadFile(path.join(__dirname, 'html', 'loading.html'));
}

function startJavaApp() {
    console.log('=== Starting Java App ===');

    let jarPath;
    const possiblePaths = [
        path.join(process.resourcesPath, 'recommend-0.0.1-SNAPSHOT.jar'),
        path.join(__dirname, 'resources', 'recommend-0.0.1-SNAPSHOT.jar'),
        path.join(process.cwd(), 'resources', 'recommend-0.0.1-SNAPSHOT.jar'),
        path.join(__dirname, '..', 'resources', 'recommend-0.0.1-SNAPSHOT.jar'),
        path.join(app.getAppPath(), 'resources', 'recommend-0.0.1-SNAPSHOT.jar')
    ];

    for (const testPath of possiblePaths) {
        if (fs.existsSync(testPath)) {
            jarPath = testPath;
            console.log(`✅ Found JAR at: ${jarPath}`);
            break;
        }
    }

    if (!jarPath) {
        console.error('❌ JAR file not found');
        showErrorScreen('JAR file not found');
        return;
    }

    // 환경변수에 API 키 설정
    const env = {
        ...process.env,
        STEAM_API_KEY: apiKeys.steamApiKey || '',
        GEMINI_API_KEY: apiKeys.geminiApiKey || '',
        SPRING_PROFILES_ACTIVE: 'default'
    };

    console.log('🚀 Starting Java process with API keys...');

    javaProcess = spawn('java', ['-jar', jarPath], {
        stdio: ['ignore', 'pipe', 'pipe'],
        cwd: path.dirname(jarPath),
        env: env
    });

    javaProcess.stdout.on('data', (data) => {
        const output = data.toString();
        console.log('Java stdout:', output);

        if (output.includes('Started RecommendApplication') ||
            output.includes('Tomcat started on port 8080')) {
            console.log('✅ Java server started successfully!');
            isJavaServerReady = true;
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
}

function showErrorScreen(errorMessage) {
    const errorHTML = `
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <title>Steam Game Recommend - Error</title>
            <style>
                body {
                    font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                    background: linear-gradient(135deg, #ff6b6b 0%, #ee5a24 100%);
                    margin: 0;
                    padding: 0;
                    display: flex;
                    justify-content: center;
                    align-items: center;
                    height: 100vh;
                    color: white;
                }
                .error-container {
                    text-align: center;
                    background: rgba(255, 255, 255, 0.1);
                    padding: 40px;
                    border-radius: 15px;
                    backdrop-filter: blur(10px);
                    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
                    max-width: 500px;
                }
                h2 { margin: 0 0 20px 0; color: #fff; }
                p { margin: 10px 0; opacity: 0.9; }
                button {
                    padding: 12px 20px;
                    border: none;
                    border-radius: 8px;
                    background: rgba(255, 255, 255, 0.2);
                    color: white;
                    font-size: 16px;
                    cursor: pointer;
                    margin-top: 20px;
                }
                button:hover {
                    background: rgba(255, 255, 255, 0.3);
                }
            </style>
        </head>
        <body>
            <div class="error-container">
                <h2>❌ Error</h2>
                <p>${errorMessage}</p>
                <p>Please check if Java is installed properly.</p>
                <button onclick="location.reload()">Retry</button>
            </div>
        </body>
        </html>
    `;
    const base64HTML = Buffer.from(errorHTML, 'utf8').toString('base64');
    mainWindow.loadURL(`data:text/html;base64,${base64HTML}`);
}

async function checkServerAndConnect() {
    console.log('=== Starting Server Connection Check ===');
    const maxAttempts = 60;
    let attempts = 0;

    while (attempts < maxAttempts) {
        try {
            const response = await fetch('http://localhost:8080', {
                method: 'GET',
                timeout: 5000
            });

            if (response.ok) {
                console.log('✅ Server connection successful!');
                mainWindow.loadURL('http://localhost:8080');
                return;
            }
        } catch (error) {
            console.log(`⏳ Server not ready yet, waiting... (${attempts + 1}/${maxAttempts})`);
        }

        await new Promise(resolve => setTimeout(resolve, 1000));
        attempts++;
    }

    console.log('❌ Server connection failed after maximum attempts');
    showErrorScreen('Server connection failed after maximum attempts');
}

app.whenReady().then(() => {
    console.log('=== App Ready ===');
    createWindow();

    app.on('activate', () => {
        if (BrowserWindow.getAllWindows().length === 0) {
            createWindow();
        }
    });
});

app.on('window-all-closed', () => {
    if (javaProcess) {
        console.log('🛑 Killing Java process...');
        javaProcess.kill();
    }

    if (process.platform !== 'darwin') {
        app.quit();
    }
});

app.on('before-quit', () => {
    if (javaProcess) {
        console.log('🛑 Killing Java process before quit...');
        javaProcess.kill();
    }
});