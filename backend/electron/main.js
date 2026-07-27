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

// Get correct app root path (fixes asar issue)
const getAppRootPath = () => {
    if (app.isPackaged) {
        // Use process.resourcesPath for built exe
        return process.resourcesPath;
    } else {
        // Use process.cwd() for development environment
        return process.cwd();
    }
};

console.log('App root path:', getAppRootPath());

// API keys file path
const getApiKeysPath = () => {
    const userDataPath = app.getPath('userData');
    return path.join(userDataPath, 'api-keys.json');
};

// Load API keys
function loadApiKeys() {
    try {
        const apiKeysPath = getApiKeysPath();
        console.log('Looking for API keys at:', apiKeysPath);
        if (fs.existsSync(apiKeysPath)) {
            const data = fs.readFileSync(apiKeysPath, 'utf8');
            apiKeys = JSON.parse(data);
            console.log('API keys loaded');
            return true;
        } else {
            console.log('No API keys file found');
            return false;
        }
    } catch (error) {
        console.error('Failed to load API keys:', error);
        return false;
    }
}

// Save API keys
function saveApiKeys(keys) {
    try {
        const apiKeysPath = getApiKeysPath();
        const userDataPath = path.dirname(apiKeysPath);

        if (!fs.existsSync(userDataPath)) {
            fs.mkdirSync(userDataPath, { recursive: true });
        }

        apiKeys = { ...apiKeys, ...keys };
        fs.writeFileSync(apiKeysPath, JSON.stringify(apiKeys, null, 2));
        console.log('API keys saved');
        return true;
    } catch (error) {
        console.error('Failed to save API keys:', error);
        throw error;
    }
}

// Steam API key validation
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

// Gemini API key validation
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

// Auto validate all API keys
async function validateAllApiKeys() {
    console.log('Starting API key validation...');

    const validationResults = {
        steam: { valid: false, message: '' },
        gemini: { valid: false, message: '' },
        hasKeys: false,
        allValid: false
    };

    // Check if API keys exist
    if (!apiKeys.steamApiKey && !apiKeys.geminiApiKey) {
        console.log('No API keys found');
        validationResults.hasKeys = false;
        return validationResults;
    }

    validationResults.hasKeys = true;

    // Validate Steam API key
    if (apiKeys.steamApiKey) {
        console.log('Validating Steam API key...');
        try {
            validationResults.steam = await validateSteamApiKey(apiKeys.steamApiKey);
            console.log(`Steam API validation result: ${validationResults.steam.valid ? 'VALID' : 'INVALID'} ${validationResults.steam.message}`);
        } catch (error) {
            console.error('Steam API validation failed:', error);
            validationResults.steam = { valid: false, message: 'Network error during validation' };
        }
    } else {
        validationResults.steam = { valid: false, message: 'Steam API key not provided' };
    }

    // Validate Gemini API key
    if (apiKeys.geminiApiKey) {
        console.log('Validating Gemini API key...');
        try {
            validationResults.gemini = await validateGeminiApiKey(apiKeys.geminiApiKey);
            console.log(`Gemini API validation result: ${validationResults.gemini.valid ? 'VALID' : 'INVALID'} ${validationResults.gemini.message}`);
        } catch (error) {
            console.error('Gemini API validation failed:', error);
            validationResults.gemini = { valid: false, message: 'Network error during validation' };
        }
    } else {
        validationResults.gemini = { valid: false, message: 'Gemini API key not provided' };
    }

    // Check overall result
    validationResults.allValid = validationResults.steam.valid && validationResults.gemini.valid;

    console.log(`API validation complete. All valid: ${validationResults.allValid ? 'YES' : 'NO'}`);
    return validationResults;
}

// Show API key validation result screen
function showValidationResultScreen(validationResults) {
    try {
        const steamStatus = validationResults.steam.valid ?
            `<div style="color: #2CC934; margin-bottom: 10px;">VALID - Steam API: ${validationResults.steam.message}</div>` :
            `<div style="color: #f44336; margin-bottom: 10px;">INVALID - Steam API: ${validationResults.steam.message}</div>`;

        const geminiStatus = validationResults.gemini.valid ?
            `<div style="color: #2CC934; margin-bottom: 10px;">VALID - Gemini API: ${validationResults.gemini.message}</div>` :
            `<div style="color: #f44336; margin-bottom: 10px;">INVALID - Gemini API: ${validationResults.gemini.message}</div>`;

        const resultHtml = `
            <div style="padding: 40px; font-family: Arial; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; min-height: 100vh; display: flex; align-items: center; justify-content: center;">
                <div style="background: rgba(255,255,255,0.1); padding: 40px; border-radius: 15px; backdrop-filter: blur(10px); width: 600px; max-width: 90%;">
                    <h2 style="text-align: center; margin-bottom: 30px;">API Key Validation Results</h2>
                    
                    <div style="margin-bottom: 25px; padding: 20px; background: rgba(255,255,255,0.1); border-radius: 10px;">
                        ${steamStatus}
                        ${geminiStatus}
                    </div>

                    ${validationResults.allValid ?
            `<div style="padding: 15px; background: rgba(76, 175, 80, 0.2); border: 1px solid #2CC934; border-radius: 8px; margin-bottom: 25px; text-align: center;">
                            <strong>All API keys are valid! Starting application...</strong>
                        </div>` :
            `<div style="padding: 15px; background: rgba(244, 67, 54, 0.2); border: 1px solid #f44336; border-radius: 8px; margin-bottom: 25px; text-align: center;">
                            <strong>Some API keys are invalid. Please update your configuration.</strong>
                        </div>`
        }

                    <div style="display: flex; gap: 10px; margin-top: 30px;">
                        ${validationResults.allValid ?
            `<button onclick="continueWithValidKeys()" style="flex: 1; padding: 12px 20px; border: none; border-radius: 8px; font-size: 16px; font-weight: 600; cursor: pointer; background: #2CC934; color: white;">Continue</button>` :
            `<button onclick="reconfigureKeys()" style="flex: 1; padding: 12px 20px; border: none; border-radius: 8px; font-size: 16px; font-weight: 600; cursor: pointer; background: #ff9800; color: white;">Reconfigure API Keys</button>
                             <button onclick="continueAnyway()" style="flex: 1; padding: 12px 20px; border: none; border-radius: 8px; font-size: 16px; font-weight: 600; cursor: pointer; background: rgba(255,255,255,0.2); color: white; border: 1px solid rgba(255,255,255,0.3);">Continue Anyway</button>`
        }
                    </div>
                </div>
            </div>
            <script>
                const { ipcRenderer } = require('electron');
                
                function continueWithValidKeys() {
                    ipcRenderer.invoke('start-main-app');
                }
                
                function reconfigureKeys() {
                    ipcRenderer.invoke('show-config-screen');
                }
                
                function continueAnyway() {
                    ipcRenderer.invoke('start-main-app');
                }

                // Auto continue if valid keys exist
                ${validationResults.allValid ? 'setTimeout(continueWithValidKeys, 3000);' : ''}
            </script>
        `;

        mainWindow.loadURL(`data:text/html,${encodeURIComponent(resultHtml)}`);
    } catch (error) {
        console.error('Error in showValidationResultScreen:', error);
        showErrorScreen('Failed to show validation result screen: ' + error.message);
    }
}

// Show validating screen
function showValidatingScreen() {
    try {
        const validatingHtml = `
            <div style="text-align:center;padding:50px;font-family:Arial;background:#1a1a2e;color:white;min-height:100vh;display:flex;flex-direction:column;justify-content:center;align-items:center;">
                <h2 style="color:#00d4ff;margin-bottom:20px;">Validating API Keys</h2>
                <p style="margin-bottom:30px;">Checking API key validation...</p>
                <div style="border:4px solid #333;border-top:4px solid #00d4ff;border-radius:50%;width:40px;height:40px;animation:spin 2s linear infinite;margin:20px auto;"></div>
                <style>@keyframes spin{0%{transform:rotate(0deg)}100%{transform:rotate(360deg)}}</style>
            </div>
        `;
        mainWindow.loadURL(`data:text/html,${encodeURIComponent(validatingHtml)}`);
    } catch (error) {
        console.error('Error in showValidatingScreen:', error);
        showErrorScreen('Failed to show validating screen: ' + error.message);
    }
}

// Create application menu
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
        console.log('Application menu created');
    } catch (error) {
        console.error('Failed to create application menu:', error);
    }
}

// Show About dialog
function showAboutDialog() {
    try {
        const aboutHtml = `
            <div style="padding: 40px; font-family: Arial; text-align: center; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; min-height: 100vh; display: flex; align-items: center; justify-content: center;">
                <div style="background: rgba(255,255,255,0.1); padding: 40px; border-radius: 15px; backdrop-filter: blur(10px); max-width: 500px;">
                    <h1 style="margin-bottom: 20px;">Steam Game Recommend</h1>
                    <p style="font-size: 18px; margin-bottom: 15px;">Version 1.0.0</p>
                    <p style="margin-bottom: 20px;">AI-powered Steam game recommendation system</p>
                    <button onclick="window.close()" style="padding: 10px 20px; background: #2CC934; color: white; border: none; border-radius: 5px; cursor: pointer; font-weight: 600;">Close</button>
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
        console.error('Failed to show about dialog:', error);
    }
}

// IPC handlers
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

// Show config screen IPC handler
ipcMain.handle('show-config-screen', () => {
    console.log('IPC: show-config-screen called');
    showConfigScreen();
});

// Revalidate API keys IPC handler
ipcMain.handle('revalidate-api-keys', async () => {
    console.log('IPC: revalidate-api-keys called');
    return await validateAllApiKeys();
});

// Start main application
function startMainApp() {
    console.log('Starting main application...');
    try {
        // Show loading screen
        showLoadingScreen();

        // Start Java app
        setTimeout(() => {
            startJavaApp();
        }, 1000);
    } catch (error) {
        console.error('Error in startMainApp:', error);
        showErrorScreen('Failed to start main application: ' + error.message);
    }
}

// Show loading screen
function showLoadingScreen() {
    try {
        const appRoot = getAppRootPath();
        let loadingPath;

        if (app.isPackaged) {
            // Use extraResources path for built exe
            loadingPath = path.join(process.resourcesPath, 'resources', 'html', 'loading.html');
        } else {
            // Development environment
            loadingPath = path.join(appRoot, 'resources', 'html', 'loading.html');
        }

        console.log('Looking for loading.html at:', loadingPath);

        if (fs.existsSync(loadingPath)) {
            console.log('Loading loading.html from file');
            mainWindow.loadFile(loadingPath);
        } else {
            console.log('loading.html not found, using inline loading screen');
            const loadingHtml = `
                <div style="text-align:center;padding:50px;font-family:Arial;background:#1a1a2e;color:white;min-height:100vh;display:flex;flex-direction:column;justify-content:center;align-items:center;">
                    <h2 style="color:#00d4ff;margin-bottom:20px;">Steam Game Recommend</h2>
                    <p style="margin-bottom:30px;">Starting server...</p>
                    <div style="border:4px solid #333;border-top:4px solid #00d4ff;border-radius:50%;width:40px;height:40px;animation:spin 2s linear infinite;margin:20px auto;"></div>
                    <style>@keyframes spin{0%{transform:rotate(0deg)}100%{transform:rotate(360deg)}}</style>
                </div>
            `;
            mainWindow.loadURL(`data:text/html,${encodeURIComponent(loadingHtml)}`);
        }
    } catch (error) {
        console.error('Error in showLoadingScreen:', error);
        showErrorScreen('Failed to show loading screen: ' + error.message);
    }
}

// Show configuration screen
function showConfigScreen() {
    try {
        const appRoot = getAppRootPath();
        let configPath;

        if (app.isPackaged) {
            // Use extraResources path for built exe
            configPath = path.join(process.resourcesPath, 'resources', 'html', 'config.html');
        } else {
            // Development environment
            configPath = path.join(appRoot, 'resources', 'html', 'config.html');
        }

        console.log('Looking for config.html at:', configPath);

        if (fs.existsSync(configPath)) {
            console.log('Loading config.html from file');
            mainWindow.loadFile(configPath);
        } else {
            console.log('config.html not found, using inline config screen');
            const configHtml = `
                <div style="padding: 40px; font-family: Arial; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; min-height: 100vh; display: flex; align-items: center; justify-content: center;">
                    <div style="background: rgba(255,255,255,0.1); padding: 40px; border-radius: 15px; backdrop-filter: blur(10px); width: 600px; max-width: 90%;">
                        <h2 style="text-align: center; margin-bottom: 30px;">API Key Configuration</h2>
                        
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
                            <button onclick="saveConfig()" style="flex: 1; padding: 12px 20px; border: none; border-radius: 8px; font-size: 16px; font-weight: 600; cursor: pointer; background: #2CC934; color: white;">Save & Continue</button>
                        </div>
                        <div id="status" style="margin-top: 15px; padding: 10px; border-radius: 5px; text-align: center; font-weight: 600; display: none;"></div>
                    </div>
                </div>
                <script>
                    const { ipcRenderer } = require('electron');
                    
                    // Load existing API keys
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
                            statusDiv.style.border = '1px solid #2CC934';
                            statusDiv.textContent = 'API keys saved successfully!';
                            setTimeout(() => { ipcRenderer.invoke('start-main-app'); }, 1500);
                        } catch (error) {
                            statusDiv.style.display = 'block';
                            statusDiv.style.background = 'rgba(244, 67, 54, 0.2)';
                            statusDiv.style.border = '1px solid #f44336';
                            statusDiv.textContent = 'Failed to save API keys: ' + error.message;
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
        console.error('Error in showConfigScreen:', error);
        showErrorScreen('Failed to show config screen: ' + error.message);
    }
}

// Check database initialization files existence and logging
function checkDatabaseFiles(workingDirectory) {
    console.log('=== Checking Database Files ===');

    const dataFolder = path.join(workingDirectory, 'data');
    const initSqlPath = path.join(dataFolder, 'init.sql');
    const dataSqlPath = path.join(dataFolder, 'data.sql');

    console.log('Data folder path:', dataFolder);
    console.log('Init SQL path:', initSqlPath);
    console.log('Data SQL path:', dataSqlPath);

    const initExists = fs.existsSync(initSqlPath);
    const dataExists = fs.existsSync(dataSqlPath);

    console.log(`init.sql exists: ${initExists ? 'YES' : 'NO'}`);
    console.log(`data.sql exists: ${dataExists ? 'YES' : 'NO'}`);

    if (initExists) {
        console.log('✓ Found init.sql - Table schema will be created');
    } else {
        console.log('⚠ init.sql not found - Table schema may not be created');
    }

    if (dataExists) {
        console.log('✓ Found data.sql - Initial data will be inserted');
    } else {
        console.log('⚠ data.sql not found - Initial data will not be inserted');
    }

    return { initExists, dataExists };
}

// Start Java app - Modified path handling and database file check
function startJavaApp() {
    console.log('=== Starting Java App ===');

    try {
        const appRoot = getAppRootPath();

        // Find JAR file path
        const possiblePaths = [
            path.join(process.resourcesPath, 'resources', 'recommend-0.0.1-SNAPSHOT.jar'),
            path.join(process.resourcesPath, 'recommend-0.0.1-SNAPSHOT.jar'),
            path.join(appRoot, 'resources', 'recommend-0.0.1-SNAPSHOT.jar'),
            path.join(appRoot, '..', 'build', 'libs', 'recommend-0.0.1-SNAPSHOT.jar')
        ];

        let jarPath = null;
        console.log('Searching for JAR file...');
        for (const testPath of possiblePaths) {
            console.log(`  Checking: ${testPath}`);
            if (fs.existsSync(testPath)) {
                jarPath = testPath;
                console.log(`  Found JAR at: ${jarPath}`);
                break;
            }
        }

        if (!jarPath) {
            console.error('JAR file not found');
            showErrorScreen('JAR file not found: recommend-0.0.1-SNAPSHOT.jar');
            return;
        }

        // Set working directory to JAR file directory
        const workingDirectory = path.dirname(jarPath);
        console.log('Working directory:', workingDirectory);

        // Check database-related paths
        const possibleDataPaths = [
            path.join(workingDirectory, 'data'),
            path.join(workingDirectory, '..', 'data'),
            path.join(process.resourcesPath, 'resources', 'data'),
            path.join(appRoot, 'resources', 'data')
        ];

        let dataPath = null;
        for (const testPath of possibleDataPaths) {
            console.log(`  Checking data folder: ${testPath}`);
            if (fs.existsSync(testPath)) {
                dataPath = testPath;
                console.log(`  Found data folder at: ${dataPath}`);
                break;
            }
        }

        // Check database file existence
        let shouldInitializeData = false;
        if (dataPath) {
            const dbPath = path.join(dataPath, 'gamedb.mv.db');
            const dbExists = fs.existsSync(dbPath);

            console.log(`Database file exists: ${dbExists ? 'YES' : 'NO'}`);

            if (dbExists) {
                console.log('✓ Existing database file found - Using existing data');
                shouldInitializeData = false;
            } else {
                console.log('ℹ No database file found - Initial data creation needed');
                shouldInitializeData = true;

                // Create data folder if it doesn't exist
                if (!fs.existsSync(dataPath)) {
                    fs.mkdirSync(dataPath, { recursive: true });
                    console.log(`Created data directory: ${dataPath}`);
                }
            }
        } else {
            dataPath = path.join(workingDirectory, 'data');
            fs.mkdirSync(dataPath, { recursive: true });
            console.log(`Created data directory: ${dataPath}`);
            shouldInitializeData = true;
        }

        const env = {
            ...process.env,
            STEAM_API_KEY: apiKeys.steamApiKey || '',
            GEMINI_API_KEY: apiKeys.geminiApiKey || '',
            SPRING_DATASOURCE_URL: `jdbc:h2:file:${dataPath ? path.join(dataPath, 'gamedb') : './data/gamedb'}`,
            INIT_DB: shouldInitializeData ? 'always' : 'never'
        };

        console.log('Environment variables:', {
            STEAM_API_KEY: env.STEAM_API_KEY ? 'SET' : 'NOT SET',
            GEMINI_API_KEY: env.GEMINI_API_KEY ? 'SET' : 'NOT SET',
            SPRING_PROFILES_ACTIVE: env.SPRING_PROFILES_ACTIVE,
            SPRING_DATASOURCE_URL: env.SPRING_DATASOURCE_URL,
            INIT_DB: env.INIT_DB
        });

        // Terminate existing process
        if (javaProcess) {
            console.log('Terminating existing Java process...');
            javaProcess.kill();
        }

        // Java execution arguments
        const javaArgs = [
            '-jar', jarPath,
            `--spring.sql.init.mode=${shouldInitializeData ? 'always' : 'never'}`,
            '--spring.jpa.defer-datasource-initialization=true',
            '--logging.level.org.springframework.jdbc=DEBUG',
            '--logging.level.org.springframework.boot.autoconfigure.sql=DEBUG',
            '--logging.level.org.hibernate.SQL=DEBUG'
        ];

        console.log('Java arguments:', javaArgs);

        javaProcess = spawn('java', javaArgs, {
            stdio: ['ignore', 'pipe', 'pipe'],
            cwd: workingDirectory,
            env: env
        });

        // Enhanced output monitoring
        javaProcess.stdout.on('data', (data) => {
            const output = data.toString();
            console.log('Java stdout:', output);

            // Monitor database initialization related logs
            if (output.includes('HikariPool')) {
                console.log('✓ Database connection pool initialized');
            }
            if (output.includes('H2 database') || output.includes('Starting embedded database')) {
                console.log('✓ H2 database connected');
            }
            if (output.includes('Executing SQL script') || output.includes('ScriptUtils')) {
                console.log('✓ Executing SQL script:', output.trim());
            }
            if (output.includes('schema.sql')) {
                console.log('✓ Schema file executed - Tables created');
            }
            if (output.includes('data.sql')) {
                console.log('✓ Data file executed - Initial data inserted');
            }
            if (output.includes('Hibernate: create table') || output.includes('Hibernate: drop table')) {
                console.log('✓ Hibernate DDL executed:', output.trim());
            }
            if (output.includes('Hibernate: insert into') || output.includes('Hibernate: select')) {
                console.log('✓ Database query executed:', output.trim());
            }
            if (output.includes('DataInitializer')) {
                console.log('✓ Data initialization component executed:', output.trim());
            }
            if (output.includes('already') && output.includes('games exist')) {
                console.log('✓ Existing data found - Skipping initialization');
            }
            if (output.includes('Inserting game data')) {
                console.log('✓ Starting insertion of ~8000 game data... (This may take some time)');
            }
            if (output.includes('Data insertion in progress')) {
                console.log('✓ Data insertion progress:', output.trim());
            }
            if (output.includes('Data insertion completed')) {
                console.log('✓ All game data insertion completed!');
            }
            if (output.includes('Foreign key check')) {
                console.log('✓ Foreign key setup:', output.trim());
            }

            // Check server startup
            if (output.includes('Started RecommendApplication') ||
                output.includes('Tomcat started on port 8888')) {
                console.log('✓ Java server startup completed!');
                isJavaServerReady = true;
                setTimeout(() => checkServerAndConnect(), 2000);
            }
        });

        javaProcess.stderr.on('data', (data) => {
            const errorOutput = data.toString();
            console.error('Java stderr:', errorOutput);

            // Special handling for database-related errors
            if (errorOutput.includes('SQLException') ||
                errorOutput.includes('DataAccessException') ||
                errorOutput.includes('JdbcTemplate')) {
                console.error('!!! Database-related error occurred !!!');
            }
        });

        javaProcess.on('close', (code) => {
            console.log(`Java process terminated with code: ${code}`);
            isJavaServerReady = false;
            if (code !== 0 && code !== null) {
                showErrorScreen(`Java application exited with error code: ${code}`);
            }
        });

        javaProcess.on('error', (error) => {
            console.error('Failed to start Java process:', error);
            isJavaServerReady = false;
            if (error.code === 'ENOENT') {
                showErrorScreen('Java is not installed or not found in PATH.');
            } else {
                showErrorScreen('Failed to start Java process: ' + error.message);
            }
        });

    } catch (error) {
        console.error('Error in startJavaApp:', error);
        showErrorScreen('Error starting Java process: ' + error.message);
    }
}

// Check server connection and load index.html
async function checkServerAndConnect() {
    console.log('=== Checking Server Connection ===');

    const maxAttempts = 15;
    let attempts = 0;

    while (attempts < maxAttempts) {
        try {
            console.log(`Connection attempt ${attempts + 1}/${maxAttempts}...`);

            const response = await fetch('http://localhost:8888/index.html', {
                method: 'GET',
                signal: AbortSignal.timeout(3000)
            });

            if (response.ok) {
                console.log('Server connection successful!');
                mainWindow.loadURL('http://localhost:8888/index.html');
                return;
            }
        } catch (error) {
            console.log(`Server not ready yet: ${error.message}`);
        }

        await new Promise(resolve => setTimeout(resolve, 1000));
        attempts++;
    }

    console.log('Server connection failed');
    showErrorScreen('Failed to connect to server after maximum attempts.');
}

// Show error screen
function showErrorScreen(message) {
    try {
        const errorHtml = `
            <div style="padding: 40px; font-family: Arial; text-align: center; background: #f44336; color: white; min-height: 100vh; display: flex; align-items: center; justify-content: center;">
                <div style="max-width: 600px;">
                    <h1>Error</h1>
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
        console.error('Error in showErrorScreen:', error);
    }
}

// Modified createWindow function - Added API key validation logic
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

        // Handle external links - open in system browser
        mainWindow.webContents.setWindowOpenHandler(({ url }) => {
            console.log('External link clicked:', url);

            // Open URL in default system browser
            shell.openExternal(url);

            // Prevent opening in Electron
            return { action: 'deny' };
        });

        // Handle navigation to external links
        mainWindow.webContents.on('will-navigate', (event, url) => {
            // Allow navigation to localhost (our app)
            if (url.startsWith('http://localhost:8888') ||
                url.startsWith('data:text/html') ||
                url.startsWith('file://')) {
                console.log('Internal navigation allowed:', url);
                return;
            }

            // Block navigation and open in external browser instead
            console.log('External navigation blocked, opening in browser:', url);
            event.preventDefault();
            shell.openExternal(url);
        });

        // Create application menu
        createApplicationMenu();

        mainWindow.once('ready-to-show', () => {
            console.log('Window ready - showing now');
            mainWindow.show();
            mainWindow.focus();

            // Open DevTools during development
            if (!app.isPackaged) {
                mainWindow.webContents.openDevTools();
            }
        });

        // API key loading and validation process
        initializeApiKeys();

    } catch (error) {
        console.error('Error creating window:', error);
        app.quit();
    }
}

// API key initialization and validation process
async function initializeApiKeys() {
    console.log('Initializing API keys...');

    try {
        // Attempt to load API keys
        const hasApiKeys = loadApiKeys();

        if (!hasApiKeys || !apiKeys.steamApiKey || !apiKeys.geminiApiKey) {
            console.log('API keys missing or incomplete - showing config screen');
            showConfigScreen();
            return;
        }

        console.log('API keys found - starting validation...');

        // Show validation progress screen
        showValidatingScreen();

        // Execute API key validation
        const validationResults = await validateAllApiKeys();

        // Process based on validation results
        if (validationResults.allValid) {
            console.log('All API keys are valid - starting main app');
            // Show result screen briefly then start main app
            showValidationResultScreen(validationResults);
        } else {
            console.log('Some API keys are invalid - showing validation results');
            showValidationResultScreen(validationResults);
        }

    } catch (error) {
        console.error('Error during API key initialization:', error);
        showErrorScreen('Failed to initialize API keys: ' + error.message);
    }
}

// App event handlers
app.whenReady().then(() => {
    console.log('=== App Ready ===');
    createWindow();
}).catch(error => {
    console.error('App failed to become ready:', error);
});

app.on('window-all-closed', () => {
    console.log('=== All Windows Closed ===');
    // Terminate Java process
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

// Clean up Java process when app quits
app.on('before-quit', () => {
    console.log('=== App Before Quit ===');
    if (javaProcess) {
        console.log('Cleaning up Java process before quit...');
        javaProcess.kill();
    }
});

console.log('=== Enhanced Main.js with API Key Validation and Database Initialization loaded successfully ===');