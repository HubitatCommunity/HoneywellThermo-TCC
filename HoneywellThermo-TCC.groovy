/**
*  Total Comfort API
*   
*  Based on Code by Eric Thomas, Edited by Bob Jase, and C Steele
*
*  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License. You may obtain a copy of the License at:
*
*      http://www.apache.org/licenses/LICENSE-2.0
*   lgk v 3 added optional outdoor temp sensors and preferences for it, also made api login required.
*  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
*  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
*  for the specific language governing permissions and limitations under the License.
* lgk version 4 supports celsius and fahrenheit with option, and now colors.
* lgk version 5, due to intermittant update failures added last update date/time tile so that you can see when it happended
* not there is a new input tzoffset which defaults to my time ie -5 which you must set .
* lgk version 6 add support for actually knowing the fan is on or not (added tile),
* and also the actual operating state ie heating,cooling or idle via new response variables.
* lgk version 7, change the new operating state to be a value vs standard tile
* to work around a bug smartthings caused in the latest 2.08 release with text wrapping.
* related also added icons to the operating state, and increase the width of the last update
* to avoid wrapping.

* lgk version 8 figured out how to do time without user input of time zone offset.. and this works with and without
* daylight saving time.
*
* lgk version 9 an indicator on the bottom which indicates if following schedule or vacation or temp hold.
* it also displays green for following schedule and red for other modes. 
* you can also select it to cancel a hold and go back to following schedule.
* however, the temp will not update till next refresh even though you have cancelled the hold.
* One problem, is that the colors are not working correctly in ios currently and the label is not 
* wrapping in  android as it is supposed to.
*
* (Bob) version 10 deals with the fact that Honeywell decided to poison the well with expired cookies
* I also changed it so that it polls for an update every 60 seconds
*
* csteele: added Cobra's Version Check code, modified debug logging to match Hubitat standards, (on/off and 30 min limit)
*    removed Relative Humidity and SmartThings "main" paragraph
*
*/
metadata {
    definition (name: "Total Comfort API B", namespace: 
                "Total Comfort API", author: "Eric Thomas, lg kahn, C Steele") {
        capability "Polling"
        capability "Thermostat"
        capability "Refresh"
        capability "Temperature Measurement"
        capability "Sensor"
//        capability "Relative Humidity Measurement"    
        command    "heatLevelUp"
        command    "heatLevelDown"
        command    "coolLevelUp"
        command    "coolLevelDown"
        command    "setFollowSchedule"
        attribute  "outdoorHumidity",    "number"
        attribute  "outdoorTemperature", "number"
        attribute  "lastUpdate",         "string"
        attribute  "followSchedule",     "string"
        attribute  "DriverAuthor",       "string"
        attribute  "DriverVersion",      "string"
        attribute  "DriverStatus",       "string"
        attribute  "DriverUpdate",       "string"

    }

//    main "temperature"
//    details(["temperature", "thermostatMode", "thermostatFanMode",   
//             "heatLevelUp", "heatingSetpoint" , "heatLevelDown", 
//             "coolLevelUp", "coolingSetpoint", "coolLevelDown" , 
//             "thermostatOperatingState", "fanOperatingState",
//             "refresh", "relativeHumidity", "outdoorTemperature",
//             "outdoorHumidity", "followSchedule","status"])

    preferences {
        input("username", "text", title: "Username", description: "Your Total Comfort User Name", required: true)
        input("password", "password", title: "Password", description: "Your Total Comfort password",required: true)
        input("honeywelldevice", "text", title: "Device ID", description: "Your Device ID", required: true)
        input ("enableOutdoorTemps", "enum", title: "Do you have the optional outdoor temperature sensor and want to enable it?", options: ["Yes", "No"], required: false, defaultValue: "No")
        input ("tempScale", "enum", title: "Fahrenheit or Celsius?", options: ["F", "C"], required: true)
        input name: "debugOutput", type: "bool", title: "Enable debug logging?", defaultValue: true
    }
}

// Driver Version   ***** with great thanks and acknowlegment to Cobra (CobraVmax) for his original version checking code ********
def setVersion(){
     state.Version = "1.1.3"
     state.InternalName = "HoneywellThermoTCC"
     sendEvent(name: "DriverAuthor", value: "cSteele", isStateChange: true)
     sendEvent(name: "DriverVersion", value: state.version, isStateChange: true)
     sendEvent(name: "DriverStatus", value: state.Status, isStateChange: true)
}

def coolLevelUp()
{
    state.DisplayUnits = settings.tempScale
    if (state.DisplayUnits == "F")
    {
        int nextLevel = device.currentValue("coolingSetpoint") + 1

        if( nextLevel > 99){
            nextLevel = 99
        }
        logDebug "Setting cool set point up to: ${nextLevel}"
        setCoolingSetpoint(nextLevel)
    }
    else
    {
        int nextLevel = device.currentValue("coolingSetpoint") + 0.5

        if( nextLevel > 37){
            nextLevel = 37
        }
        logDebug "Setting cool set point up to: ${nextLevel}"
        setCoolingSetpoint(nextLevel)

    }
}

def coolLevelDown()
{
    state.DisplayUnits = settings.tempScale
    if (state.DisplayUnits == "F")
    {
        int nextLevel = device.currentValue("coolingSetpoint") - 1

        if( nextLevel < 50){
            nextLevel = 50
        }
        logDebug "Setting cool set point down to: ${nextLevel}"
        setCoolingSetpoint(nextLevel)
    }
    else
    {
        double nextLevel = device.currentValue("coolingSetpoint") - 0.5

        if( nextLevel < 10){
            nextLevel = 10
        }
        logDebug "Setting cool set point down to: ${nextLevel}"
        setCoolingSetpoint(nextLevel)

    }
}



def heatLevelUp()
{
    state.DisplayUnits = settings.tempScale
    if (state.DisplayUnits == "F")
    {
        logDebug "in fahrenheit level up"
        int nextLevel = device.currentValue("heatingSetpoint") + 1

        if( nextLevel > 90){
            nextLevel = 90
        }
        logDebug "Setting heat set point up to: ${nextLevel}"
        setHeatingSetpoint(nextLevel)
    }
    else
    {
        logDebug "in celsius level up"
        double nextLevel = device.currentValue("heatingSetpoint") + 0.5
        if( nextLevel > 33){
            nextLevel = 33
        }
        logDebug "Setting heat set point up to: ${nextLevel}"
        setHeatingSetpoint(nextLevel)
    }
}


def heatLevelDown()
{
    state.DisplayUnits = settings.tempScale
    if (state.DisplayUnits == "F")
    {
        logDebug "in fahrenheit level down"
        int nextLevel = device.currentValue("heatingSetpoint") - 1
        if( nextLevel < 40){
            nextLevel = 40
        }
        logDebug "Setting heat set point down to: ${nextLevel}"
        setHeatingSetpoint(nextLevel)
    }
    else
    {
        logDebug "in celsius level down"
        double nextLevel = device.currentValue("heatingSetpoint") - 0.5
        if( nextLevel < 4){
            nextLevel = 4
       }
        logDebug "Setting heat set point down to: ${nextLevel}"
        setHeatingSetpoint(nextLevel)
    }
}

// parse events into attributes
def parse(String description) {

}

// handle commands
def setHeatingSetpoint(Double temp)
{
    device.data.SystemSwitch = 'null' 
    device.data.HeatSetpoint = temp
    device.data.CoolSetpoint = 'null'
    device.data.HeatNextPeriod = 'null'
    device.data.CoolNextPeriod = 'null'
    device.data.StatusHeat='1'
    device.data.StatusCool='1'
    device.data.FanMode = 'null'
    setStatus()

    if(device.data.SetStatus==1)
    {
        sendEvent(name: 'heatingSetpoint', value: temp as double)

    }	
}

def setHeatingSetpoint(temp) {
    device.data.SystemSwitch = 'null' 
    device.data.HeatSetpoint = temp
    device.data.CoolSetpoint = 'null'
    device.data.HeatNextPeriod = 'null'
    device.data.CoolNextPeriod = 'null'
    device.data.StatusHeat='1'
    device.data.StatusCool='1'
    device.data.FanMode = 'null'
    setStatus()

    if(device.data.SetStatus==1)
    {
        sendEvent(name: 'heatingSetpoint', value: temp as Integer)
    }
}

def setFollowSchedule() {
    logDebug "in set follow schedule"
    device.data.SystemSwitch = 'null' 
    device.data.HeatSetpoint = 'null'
    device.data.CoolSetpoint = 'null'
    device.data.HeatNextPeriod = 'null'
    device.data.CoolNextPeriod = 'null'
    device.data.StatusHeat='0'
    device.data.StatusCool='0'
    device.data.FanMode = 'null'
    setStatus()

    if(device.data.SetStatus==1)
    {
        logDebug "Successfully sent follow schedule.!"
        runIn(60,"getStatus")
    }
}


def setCoolingSetpoint(double temp) {
    device.data.SystemSwitch = 'null' 
    device.data.HeatSetpoint = 'null'
    device.data.CoolSetpoint = temp
    device.data.HeatNextPeriod = 'null'
    device.data.CoolNextPeriod = 'null'
    device.data.StatusHeat='1'
    device.data.StatusCool='1'
    device.data.FanMode = 'null'
    setStatus()

    if(device.data.SetStatus==1)
    {
        sendEvent(name: 'coolingSetpoint', value: temp as double)

    }
}

def setCoolingSetpoint(temp) {
    device.data.SystemSwitch = 'null' 
    device.data.HeatSetpoint = 'null'
    device.data.CoolSetpoint = temp
    device.data.HeatNextPeriod = 'null'
    device.data.CoolNextPeriod = 'null'
    device.data.StatusHeat='1'
    device.data.StatusCool='1'
    device.data.FanMode = 'null'
    setStatus()

    if(device.data.SetStatus==1)
    {
        sendEvent(name: 'coolingSetpoint', value: temp as Integer)
    }
}

def setTargetTemp(temp) {
    device.data.SystemSwitch = 'null' 
    device.data.HeatSetpoint = temp
    device.data.CoolSetpoint = temp
    device.data.HeatNextPeriod = 'null'
    device.data.CoolNextPeriod = 'null'
    device.data.StatusHeat='1'
    device.data.StatusCool='1'
    device.data.FanMode = 'null'
    setStatus()
}

def setTargetTemp(double temp) {
    device.data.SystemSwitch = 'null' 
    device.data.HeatSetpoint = temp
    device.data.CoolSetpoint = temp
    device.data.HeatNextPeriod = 'null'
    device.data.CoolNextPeriod = 'null'
    device.data.StatusHeat='1'
    device.data.StatusCool='1'
    device.data.FanMode = 'null'
    setStatus()
}

def off() {
    setThermostatMode(2)
}

def auto() {
    setThermostatMode(4)
}

def heat() {
    setThermostatMode(1)
}

def emergencyHeat() {

}

def cool() {
    setThermostatMode(3)
}

def setThermostatMode(mode) {
    device.data.SystemSwitch = mode 
    device.data.HeatSetpoint = 'null'
    device.data.CoolSetpoint = 'null'
    device.data.HeatNextPeriod = 'null'
    device.data.CoolNextPeriod = 'null'
    device.data.StatusHeat=1
    device.data.StatusCool=1
    device.data.FanMode = 'null'

    setStatus()

    def switchPos

    if(mode==1)
    switchPos = 'heat'
    if(mode==2)
    switchPos = 'off'
    if(mode==3)
    switchPos = 'cool'
    /* lgk modified my therm has pos 5 for auto vision pro */
    if(mode==4 || swithPos == 5)
    switchPos = 'auto'

    if(device.data.SetStatus==1)
    {
        sendEvent(name: 'thermostatMode', value: switchPos)
    }
}

def fanOn() {
    setThermostatFanMode(1)
}

def fanAuto() {
    setThermostatFanMode(0)
}

def fanCirculate() {
    setThermostatFanMode(2)
}

def setThermostatFanMode(mode) {    
    device.data.SystemSwitch = 'null' 
    device.data.HeatSetpoint = 'null'
    device.data.CoolSetpoint = 'null'
    device.data.HeatNextPeriod = 'null'
    device.data.CoolNextPeriod = 'null'
    device.data.StatusHeat='null'
    device.data.StatusCool='null'
    device.data.FanMode = mode

    setStatus()

    def fanMode

    if(mode==0)
    fanMode = 'auto'
    if(mode==1)
    fanMode = 'on'
    if(mode==2)
    fanMode = 'circulate'

    if(device.data.SetStatus==1)
    {
        sendEvent(name: 'thermostatFanMode', value: fanMode)    
    }

}

def poll() {
    refresh()
    runIn(60, poll) // refresh status every 60 seconds
}


def setStatus() {

    device.data.SetStatus = 0

    login()
    logDebug "Executing 'setStatus'"
    def today= new Date()
    logDebug "https://www.mytotalconnectcomfort.com/portal/Device/SubmitControlScreenChanges"
    logDebug "setting heat setpoint to $device.data.HeatSetpoint"
    logDebug "setting cool setpoint to $device.data.CoolSetpoint"

    def params = [
        uri: "https://www.mytotalconnectcomfort.com/portal/Device/SubmitControlScreenChanges",
        headers: [
            'Accept': 'application/json, text/javascript, */*; q=0.01', // */ comment
            'DNT': '1',
            'Accept-Encoding': 'gzip,deflate,sdch',
            'Cache-Control': 'max-age=0',
            'Accept-Language': 'en-US,en,q=0.8',
            'Connection': 'keep-alive',
            'Host': 'mytotalconnectcomfort.com',
            'Referer': "https://www.mytotalconnectcomfort.com/portal/Device/Control/${settings.honeywelldevice}",
            'X-Requested-With': 'XMLHttpRequest',
            'User-Agent': 'Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/28.0.1500.95 Safari/537.36',
            'Cookie': device.data.cookiess        ],
        body: [ DeviceID: "${settings.honeywelldevice}", SystemSwitch : device.data.SystemSwitch ,HeatSetpoint : 
               device.data.HeatSetpoint, CoolSetpoint: device.data.CoolSetpoint, HeatNextPeriod: 
               device.data.HeatNextPeriod,CoolNextPeriod:device.data.CoolNextPeriod,StatusHeat:device.data.StatusHeat,
               StatusCool:device.data.StatusCool,FanMode:device.data.FanMode,ThermostatUnits: settings.tempScale]

    ]

    logDebug "params = $params"
    httpPost(params) { response ->
        logDebug "Request was successful, $response.status"

    }

    logDebug "SetStatus is 1 now"
    device.data.SetStatus = 1

}

def getStatus() {
    logDebug "Executing getStatus"
    logDebug "enable outside temps = $enableOutdoorTemps"
    def today= new Date()
    logDebug "https://www.mytotalconnectcomfort.com/portal/Device/CheckDataSession/${settings.honeywelldevice}?_=$today.time"

    def params = [
        uri: "https://www.mytotalconnectcomfort.com/portal/Device/CheckDataSession/${settings.honeywelldevice}",
        headers: [
            'Accept': '*/*',
            'DNT': '1',
            'Cache' : 'false',
            'dataType': 'json',
            'Accept-Encoding': 'plain',
            'Cache-Control': 'max-age=0',
            'Accept-Language': 'en-US,en,q=0.8',
            'Connection': 'keep-alive',
            'Referer': 'https://www.mytotalconnectcomfort.com/portal',
            'X-Requested-With': 'XMLHttpRequest',
            'User-Agent': 'Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/28.0.1500.95 Safari/537.36',
            'Cookie': device.data.cookiess        ],
    ]

    logDebug "doing request"

    httpGet(params) { response ->
        logDebug "Request was successful, $response.status"
        //logDebug "data = $response.data"
        logDebug "ld = $response.data.latestData"

        def curTemp = response.data.latestData.uiData.DispTemperature
        def fanMode = response.data.latestData.fanData.fanMode
        def switchPos = response.data.latestData.uiData.SystemSwitchPosition
        def coolSetPoint = response.data.latestData.uiData.CoolSetpoint
        def heatSetPoint = response.data.latestData.uiData.HeatSetpoint
        def statusCool = response.data.latestData.uiData.StatusCool
        def statusHeat = response.data.latestData.uiData.StatusHeat
        def curHumidity = response.data.latestData.uiData.IndoorHumidity
        def Boolean hasOutdoorHumid = response.data.latestData.uiData.OutdoorHumidityAvailable
        def Boolean hasOutdoorTemp = response.data.latestData.uiData.OutdoorTemperatureAvailable
        def curOutdoorHumidity = response.data.latestData.uiData.OutdoorHumidity
        def curOutdoorTemp = response.data.latestData.uiData.OutdoorTemperature
        def displayUnits = response.data.latestData.uiData.DisplayUnits
        def fanIsRunning = response.data.latestData.fanData.fanIsRunning
        def equipmentStatus = response.data.latestData.uiData.EquipmentOutputStatus

        def holdTime = response.data.latestData.uiData.TemporaryHoldUntilTime
        def vacationHold = response.data.latestData.uiData.IsInVacationHoldMode

        logDebug "got holdTime = $holdTime"
        logDebug "got Vacation Hold = $vacationHold"

        if (holdTime != 0) 
        {  logDebug "sending temporary hold"
         sendEvent(name: 'followSchedule', value: "TemporaryHold")
        }

        if (vacationHold == true)
        { logDebug "sending vacation hold"
         sendEvent(name: 'followSchedule', value: "VacationHold")
        }

        if (vacationHold == false && holdTime == 0)
        {
            logDebug "Sending following schedule"
            sendEvent(name: 'followSchedule', value: "FollowingSchedule")
        }
        //  logDebug "displayUnits = $displayUnits"
        state.DisplayUnits = $displayUnits

        //Operating State Section 
        //Set the operating state to off 

        def operatingState = "Unknown"

        // lgk operating state not working here.. shows both on ie 1 when heat doesnt go on to 67 and heat till 76  and current is 73 
        //Check the status of heat and cool 

        // lgk old method now use equipment status
        if (equipmentStatus == 1) {
            operatingState = "Heating"
        } else if (equipmentStatus == 2) {
            operatingState = "Cooling"
        } else if (equipmentStatus == 0) {
            operatingState = "Idle"
        } else {
            operatingState = "Unknown"
        }

        log.trace("Set operating State to: ${operatingState}")        

        // set fast state
        def fanState = "Unknown"

        if (fanIsRunning == true)
        fanState = "On"
        else fanState = "Idle" 

        log.trace("Set Fan operating State to: ${fanState}")        

        //End Operating State

        //  logDebug curTemp
        // logDebug fanMode
        // logDebug switchPos

        //fan mode 0=auto, 2=circ, 1=on

        if(fanMode==0)
        fanMode = 'auto'
        if(fanMode==1)
        fanMode = 'on'
        if(fanMode==2)
        fanMode = 'circulate'

        if(switchPos==1)
        switchPos = 'heat'
        if(switchPos==2)
        switchPos = 'off'
        if(switchPos==3)
        switchPos = 'cool'
        if(switchPos==4 || switchPos==5)
        switchPos = 'auto'

        def formattedCoolSetPoint = String.format("%5.1f", coolSetPoint)
        def formattedHeatSetPoint = String.format("%5.1f", heatSetPoint)
        def formattedTemp = String.format("%5.1f", curTemp)

        def finalCoolSetPoint = formattedCoolSetPoint as BigDecimal
        def finalHeatSetPoint = formattedHeatSetPoint as BigDecimal
        def finalTemp = formattedTemp as BigDecimal

        //Send events 
        sendEvent(name: 'thermostatOperatingState', value: operatingState)
        sendEvent(name: 'fanOperatingState', value: fanState)
        sendEvent(name: 'thermostatFanMode', value: fanMode)
        sendEvent(name: 'thermostatMode', value: switchPos)
        sendEvent(name: 'coolingSetpoint', value: finalCoolSetPoint )
        sendEvent(name: 'heatingSetpoint', value: finalHeatSetPoint )
        sendEvent(name: 'temperature', value: finalTemp, state: switchPos)
        sendEvent(name: 'relativeHumidity', value: curHumidity as Integer)


        //logDebug "location = $location.name tz = $location.timeZone"
        def now = new Date().format('MM/dd/yyyy h:mm a',location.timeZone)

        //def now = new Date()
        //def tf = new java.text.SimpleDateFormat("MM/dd/yyyy h:mm a")
        //tf.setTimeZone(TimeZone.getTimeZone("GMT${settings.tzOffset}"))
        //def newtime = "${tf.format(now)}" as String   
        // sendEvent(name: "lastUpdate", value: newtime, descriptionText: "Last Update: $newtime")
        sendEvent(name: "lastUpdate", value: now, descriptionText: "Last Update: $now")


        if (enableOutdoorTemps == "Yes")
        {

            if (hasOutdoorHumid)
            {
                sendEvent(name: 'outdoorHumidity', value: curOutdoorHumidity as Integer)
            }

            if (hasOutdoorTemp)
            {
                sendEvent(name: 'outdoorTemperature', value: curOutdoorTemp as Integer)
            }
        }
    }
}


def getHumidifierStatus()
{
    def params = [
        uri: "https://www.mytotalconnectcomfort.com/portal/Device/Menu/GetHumData/${settings.honeywelldevice}",
        headers: [
            'Accept': '*/*',
            'DNT': '1',
            'dataType': 'json',
            'cache': 'false',
            'Accept-Encoding': 'plain',
            'Cache-Control': 'max-age=0',
            'Accept-Language': 'en-US,en,q=0.8',
            'Connection': 'keep-alive',
            'Host': 'rs.alarmnet.com',
            'Referer': 'https://www.mytotalconnectcomfort.com/portal/Menu/${settings.honeywelldevice}',
            'X-Requested-With': 'XMLHttpRequest',
            'User-Agent': 'Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/28.0.1500.95 Safari/537.36',
            'Cookie': device.data.cookiess        ],
    ]
    httpGet(params) { response ->
        logDebug "GetHumidity Request was successful, $response.status"
        logDebug "response = $response.data"

        //  logDebug "ld = $response.data.latestData"
        //  logDebug "humdata = $response.data.latestData.humData"

        log.trace("lowerLimit: ${response.data.latestData.humData.lowerLimit}")        
        log.trace("upperLimit: ${response.data.humData.upperLimit}")        
        log.trace("SetPoint: ${response.data.humData.Setpoint}")        
        log.trace("DeviceId: ${response.data.humData.DeviceId}")        
        log.trace("IndoorHumidity: ${response.data.humData.IndoorHumidity}")        

    }
}

def api(method, args = [], success = {}) {

}

// Need to be logged in before this is called. So don't call this. Call api.
def doRequest(uri, args, type, success) {

}

def refresh() {
    logDebug "Executing 'refresh'"
    def unit = getTemperatureScale()
    logDebug "units = $unit"
    login()
    //getHumidifierStatus()
    getStatus()
}

def login() {  
    logDebug "Executing 'login'"

    def params = [
        uri: 'https://www.mytotalconnectcomfort.com/portal',
        headers: [
            'Content-Type': 'application/x-www-form-urlencoded',
            'Accept': 'application/json, text/javascript, */*; q=0.01', // */
            'Accept-Encoding': 'sdch',
            'Host': 'www.mytotalconnectcomfort.com',
            'DNT': '1',
            'Origin': 'www.mytotalconnectcomfort.com/portal/',
            'User-Agent': 'Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/28.0.1500.95 Safari/537.36'
        ],
        body: [timeOffset: '240', UserName: "${settings.username}", Password: "${settings.password}", RememberMe: 'false']
    ]

    device.data.cookiess = ''

    httpPost(params) { response ->
        logDebug "Request was successful, $response.status"
        logDebug response.headers
        String allCookies = ""

        //response.getHeaders('Set-Cookie').each {
        //              logDebug "---Set-Cookie: ${it.value}"
        //      }

        response.getHeaders('Set-Cookie').each {
            String cookie = it.value.split(';|,')[0]
            Boolean skipCookie = false
            def expireParts = it.value.split('expires=')

            try {
                def cookieSegments = it.value.split(';')
                for (int i = 0; i < cookieSegments.length; i++) {
                    def cookieSegment = cookieSegments[i]
                    String cookieSegmentName = cookieSegment.split('=')[0]

                    if (cookieSegmentName.trim() == "expires") {
                        String expiration = cookieSegment.split('=')[1] 

                        Date expires = new Date(expiration)
                        Date newDate = new Date() // right now

                        if (expires < newDate ) {
                            skipCookie=true
                            //logDebug "-skip cookie: $it.value"
                        } else {
                            //logDebug "+not skipping cookie: expires=$expires. now=$newDate. cookie: $it.value"
                        }

                    } 
                }
            }
            catch (e) {
                logDebug "!error when checking expiration date: $e ($expiration) [$expireParts.length] {$it.value}"
            }

            allCookies = allCookies + it.value + ';'

            if(cookie != ".ASPXAUTH_TH_A=") {
                if (it.value.split('=')[1].trim() != "") {
                    if (!skipCookie) {
                        logDebug "Adding cookie to collection: $cookie"
                        device.data.cookiess = device.data.cookiess+cookie+';'
                    }
                }
            }
        }
        logDebug "cookies: $device.data.cookiess"
    }
}

def isLoggedIn() {
    if(!device.data.auth) {
        logDebug "No device.data.auth"
        return false
    }

    def now = new Date().getTime();
    return device.data.auth.expires_in > now
}

def updated()
{
   logDebug "in updated"
    state.DisplayUnits = settings.tempScale
    logDebug "display units now = $state.DisplayUnits"
    if (debugOutput) runIn(1800,logsOff)
    version()
}

def installed() {
    state.DisplayUnits = settings.tempScale

    logDebug "display units now = $state.DisplayUnits"
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("debugOutput",[value:"false",type:"bool"])
}

private logDebug(msg) {
	if (settings?.debugOutput || settings?.debugOutput == null) {
		log.debug "$msg"
	}
}


// Driver Version   ***** with great thanks and acknowlegment to Cobra (CobraVmax) for his original version checking code ********
def version(){
    unschedule()
    schedule("0 0 8 ? * FRI *", updateCheck)  // Cron schedule - How often to perform the update check - (This example is 8am every Friday)
    updateCheck()
}

def updateCheck(){
    setVersion()
	 def paramsUD = [uri: "https://hubitatcommunity.github.io/HoneywellThermo-TCC/versions.json" ]  // This is the URI & path to your hosted JSON file
       try {
           httpGet(paramsUD) { respUD ->
//           log.warn " Version Checking - Response Data: ${respUD.data}"   // Troubleshooting Debug Code - Uncommenting this line should show the JSON response from your webserver
           def copyrightRead = (respUD.data.copyright)
           state.Copyright = copyrightRead
           def newVerRaw = (respUD.data.versions.Driver.(state.InternalName))
           def newVer = (respUD.data.versions.Driver.(state.InternalName).replace(".", ""))
           def currentVer = state.Version.replace(".", "")
           state.UpdateInfo = (respUD.data.versions.UpdateInfo.Driver.(state.InternalName))
           state.author = (respUD.data.author)
           
           if(newVer == "NLS"){
               state.Status = "<b>** This driver is no longer supported by $state.author  **</b>"       
               log.warn "** This driver is no longer supported by $state.author **"      
           }           
           else if(currentVer < newVer){
               state.Status = "<b>New Version Available (Version: $newVerRaw)</b>"
               log.warn "** There is a newer version of this driver available  (Version: $newVerRaw) **"
               log.warn "** $state.UpdateInfo **"
           } else { 
           	state.Status = "Current"
           	log.info "You are using the current version of this driver"
           }
         }
       } 

       catch (e) {
           log.error "Something went wrong: CHECK THE JSON FILE AND IT'S URI -  $e"
           }
           
       if(state.Status == "Current"){
           state.UpdateInfo = "N/A"
           sendEvent(name: "DriverUpdate", value: state.UpdateInfo, isStateChange: true)
           sendEvent(name: "DriverStatus", value: state.Status, isStateChange: true)
       } else {
           sendEvent(name: "DriverUpdate", value: state.UpdateInfo, isStateChange: true)
           sendEvent(name: "DriverStatus", value: state.Status, isStateChange: true)
       }   
 	 sendEvent(name: "DriverAuthor", value: state.author, isStateChange: true)
    	 sendEvent(name: "DriverVersion", value: state.Version, isStateChange: true)
}
