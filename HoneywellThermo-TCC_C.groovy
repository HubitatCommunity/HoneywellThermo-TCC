/**
 * IMPORT URL: https://raw.githubusercontent.com/HubitatCommunity/HoneywellThermo-TCC/master/HoneywellThermo-TCC_C.groovy
 *
 *  Total Comfort API
 *   
 *  Based on Code by Eric Thomas, Edited by Bob Jase, and C Steele
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * csteele: v1.3.13  added Initialize per ryanmellish suggestion to jumpstart polling after hub power cycle.
 *     jvm: v1.3.12  Enabled Humidity child device.
 * csteele: v1.3.11  refactored support for lastRunningMode as data vs attribute
 * csteele: v1.3.10  add support for lastRunningMode which directly follows thermostatMode
 *                    refactored 'switch/case' code into a Map for fan and operating state
 * nvious1: v1.3.9   adding "fan only" operating mode for when the equipment is off but the fan is running. Added 3 min polling option. 
 * csteele: v1.3.8   made "description logging is" optional and info
 *                    added explicit check for cooling in getStatusHandler
 * csteele: v1.3.7   removed state.displayunits as unused. Everything has already been using the Hub's location.temperatureScale,
 *                    which meant that installed() was redundant too.
 *    jvm : v1.3.6   added range checking for changes to heating and cooling setpoints. 
 *                    Outdoor thermostate creates as a child device. 
 *                    Fixed bugs in use of tccSite variable.
 * csteele:           corrected sendEvent("humidity") 
 *                    corrected operating state to track EquipmentOutputStatus
 *                    refactored cool/heat up/down
 *     jvm:           limit checked temperature set points
 * csteele: v1.3.5   added "%" to humidity and centralized temp scale
 *     jvm: v1.3.4   added "°F" or "°C" unit to temp and setpoint events. Fixed thermostateMode being set to a temperature value.
 * csteele: v1.3.2   centralized Honeywell site url as "tccSite"
 * csteele: v1.3.1   updated to v2 of updateCheck
 * csteele: v1.3.0   converted to asynchttp where possible.
 * csteele: v1.2.3   communications with TCC changed and now Mode and Fan need to be numbers
 *                    Operating State reflects the ENUM values ("Unknown" isn't acceptable)
 * csteele: v1.2.2   replaced F/C selection with value from Location in the hub.
 * csteele: v1.2     option of polling interval, off through 60 min. added descTextEnable for Description logging.
 * csteele: v1.1.5   allow option of permanent or temporary hold.
 * csteele: v1.1     merged Pull Request from rylatorr: Use permanent hold instead of temporary
 * csteele: v1.0     added Cobra's Version Check code, modified debug logging to match Hubitat standards, (on/off and 30 min limit)
 *                      removed Relative Humidity and SmartThings "main" paragraph
 *
 * (Bob) version 10 deals with the fact that Honeywell decided to poison the well with expired cookies
 *    I also changed it so that it polls for an update every 60 seconds
 * lgk version 9 an indicator on the bottom which indicates if following schedule or vacation or temp hold.
 *    it also displays green for following schedule and red for other modes. 
 *    you can also select it to cancel a hold and go back to following schedule.
 *    however, the temp will not update till next refresh even though you have cancelled the hold.
 *    One problem, is that the colors are not working correctly in ios currently and the label is not 
 *    wrapping in  android as it is supposed to.
 * lgk version 8 figured out how to do time without user input of time zone offset.. and this works with and without
 *    daylight saving time.
 * lgk version 7, change the new operating state to be a value vs standard tile
 *    to work around a bug smartthings caused in the latest 2.08 release with text wrapping.
 *    related also added icons to the operating state, and increase the width of the last update
 *    to avoid wrapping.
 * lgk version 6 add support for actually knowing the fan is on or not (added tile),
 *    and also the actual operating state ie heating,cooling or idle via new response variables.
 * lgk version 5, due to intermittant update failures added last update date/time tile so that you can see when it happended
 *    not there is a new input tzoffset which defaults to my time ie -5 which you must set .
 * lgk version 4 supports celsius and fahrenheit with option, and now colors.
 * lgk v 3 added optional outdoor temp sensors and preferences for it, also made api login required.
 *
*/

 public static String version()     {  return "v1.3.13"  }
 public static String tccSite() 	{  return "www.mytotalconnectcomfort.com"  }

metadata {
    definition (name: "Total Comfort API C", namespace: "csteele", author: "Eric Thomas, lg kahn, C Steele", importUrl: "https://raw.githubusercontent.com/HubitatCommunity/HoneywellThermo-TCC/master/HoneywellThermo-TCC_C.groovy") {
        capability "Polling"
        capability "Thermostat"
        capability "Refresh"
        capability "Temperature Measurement"
        capability "Sensor"
        capability "Relative Humidity Measurement"
        capability "Initialize"   
        command    "heatLevelUp"
        command    "heatLevelDown"
        command    "coolLevelUp"
        command    "coolLevelDown"
        command    "setFollowSchedule"
        attribute  "outdoorHumidity",    "number"
        attribute  "outdoorTemperature", "number"
        attribute  "lastUpdate",         "string"
        attribute  "followSchedule",     "string"

//	  command "updateCheck"			// **---** delete for Release
    }

    preferences {
       input name: "username", type: "text", title: "Username", description: "Your Total Comfort User Name", required: true
       input name: "password", type: "password", title: "Password", description: "Your Total Comfort password",required: true
       input name: "honeywelldevice", type: "text", title: "Device ID", description: "Your Device ID", required: true
       input name: "enableOutdoorTemps", type: "enum", title: "Do you have the optional outdoor temperature sensor and want to enable it?", options: ["Yes", "No"], required: false, defaultValue: "No"
       input name: "enableHumidity", type: "enum", title: "Do you have the optional Humidity sensor and want to enable it?", options: ["Yes", "No"], required: false, defaultValue: "No"
       input name: "setPermHold", type: "enum", title: "Will Setpoints be temporary or permanent?", options: ["Temporary", "Permanent"], required: false, defaultValue: "Temporary"
       input name: "pollIntervals", type: "enum", title: "Set the Poll Interval.", options: [0:"off", 60:"1 minute", 120:"2 minutes", 180:"3 minutes", 300:"5 minutes",600:"10 minutes",900:"15 minutes",1800:"30 minutes",3600:"60 minutes"], required: true, defaultValue: "600"
       input name: "debugOutput", type: "bool", title: "Enable debug logging?", defaultValue: true
       input name: "descTextEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
    }
}

// parse events into attributes
def parse(String description) {

}


// handle commands
def coolLevelUp()   {  if (location.temperatureScale == "F")  {  setCoolingSetpoint(device.currentValue("coolingSetpoint") + 1) } else { setCoolingSetpoint( (Double) device.currentValue("coolingSetpoint") + 0.5) }}
def coolLevelDown() {  if (location.temperatureScale == "F")  {  setCoolingSetpoint(device.currentValue("coolingSetpoint") - 1) } else { setCoolingSetpoint( (Double) device.currentValue("coolingSetpoint") - 0.5) }}
def heatLevelUp()   {  if (location.temperatureScale == "F")  {  setCoolingSetpoint(device.currentValue("heatingSetpoint") + 1) } else { setCoolingSetpoint( (Double) device.currentValue("heatingSetpoint") + 0.5) }}
def heatLevelDown() {  if (location.temperatureScale == "F")  {  setCoolingSetpoint(device.currentValue("heatingSetpoint") - 1) } else { setCoolingSetpoint( (Double) device.currentValue("heatingSetpoint") - 0.5) }}


def setCoolingSetpoint(temp) {
        if (temp < state.coolLowerSetptLimit) 
        {
            temp = state.coolLowerSetptLimit
            log.warn "Set Point out of range, low" 
        }
        if (temp > state.coolUpperSetptLimit) 
        {
            temp = state.coolUpperSetptLimit
            log.warn "Set Point out of range, high" 
        }
        deviceDataInit(state.PermHold)
        device.data.CoolSetpoint = temp
        log.info "Setting cool setpoint to: ${temp}"
        setStatus()
        
        if(device.data.SetStatus==1)
        {
            sendEvent(name: 'coolingSetpoint', value: temp as Integer, unit:device.data.unit)
        }
}

def setCoolingSetpoint(double temp) {
         if (temp < state.coolLowerSetptLimit) 
         {
             temp = state.coolLowerSetptLimit
             log.warn "Set Point out of range, low" 
         }
         if (temp > state.coolUpperSetptLimit) 
         {
             temp = state.coolUpperSetptLimit
             log.warn "Set Point out of range, high" 
         }
        deviceDataInit(state.PermHold)
        device.data.CoolSetpoint = temp
        log.info "Setting cool set point down to: ${temp}"
        setStatus()
        
        if(device.data.SetStatus==1)
        {
            sendEvent(name: 'coolingSetpoint', value: temp as double, unit:device.data.unit)
        }
}


def setHeatingSetpoint(temp) {
         if (temp < state.heatLowerSetptLimit) 
         {
             temp = state.heatLowerSetptLimit
             log.warn "Set Point out of range, low" 
         }
         if (temp > state.heatUpperSetptLimit) 
         {
             temp = state.heatUpperSetptLimit
             log.warn "Set Point out of range, high" 
         }
        deviceDataInit(state.PermHold)
        device.data.HeatSetpoint = temp
        log.info "Setting heat setpoint to: ${temp}"
        setStatus()
        
        if(device.data.SetStatus==1)
        {
            sendEvent(name: 'heatingSetpoint', value: temp as Integer, unit:device.data.unit)
        }
}

def setHeatingSetpoint(Double temp)
{
         if (temp < state.heatLowerSetptLimit) 
         {
             temp = state.heatLowerSetptLimit
             log.warn "Set Point out of range, low" 
         }
         if (temp > state.heatUpperSetptLimit) 
         {
             temp = state.heatUpperSetptLimit
             log.warn "Set Point out of range, high" 
         }
        deviceDataInit(state.PermHold)
        device.data.HeatSetpoint = temp
        log.info "Setting heat set point down to: ${temp}"
        setStatus()
        
        if(device.data.SetStatus==1)
        {
        	sendEvent(name: 'heatingSetpoint', value: temp as double, unit:device.data.unit)
        }	
}


def setFollowSchedule() {
	if (debugOutput) log.debug "in set follow schedule"
	deviceDataInit('0')
//	device.data.HeatSetpoint = temp
	setStatus()

	if(device.data.SetStatus==1)
	{
        if (debugOutput) log.debug "Successfully sent follow schedule.!"
//        runIn(60,getStatus)
//        runEvery1Minutes (getStatus)
	}
}


def setTargetTemp(temp) {
	if ((temp > state.coolLowerSetptLimit) || (temp < state.coolUpperSetptLimit)) {
		deviceDataInit(state.PermHold)
		device.data.HeatSetpoint = temp
		device.data.CoolSetpoint = temp
		setStatus()
	} else { log.warn "Set Point out of range: $temp" }
}

def setTargetTemp(double temp) {
	if ((temp > state.coolLowerSetptLimit) || (temp < state.coolUpperSetptLimit)) {
		deviceDataInit(state.PermHold)
		device.data.HeatSetpoint = temp
		device.data.CoolSetpoint = temp
		setStatus()
	} else { log.warn "Set Point out of range: $temp" }
}

def off() {
	setThermostatMode('off')
}

def auto() {
	setThermostatMode('auto')
}

def heat() {
	setThermostatMode('heat')
}

def cool() {
	setThermostatMode('cool')
}

def emergencyHeat() {

}

def setThermostatMode(mode) {
	Map modeMap = [auto:5, cool:3, heat:1, off:2]
	if (debugOutput) log.debug "setThermostatMode: $mode"
	deviceDataInit(null)

	device.data.SystemSwitch = modeMap.find{ mode == it.key }?.value
	setStatus()
	
	if(device.data.SetStatus==1)
	{
	    sendEvent(name: 'thermostatMode', value: mode)
	    lrM(mode) 
	}
}

def fanOn() {
    setThermostatFanMode('on')
}

def fanAuto() {
    setThermostatFanMode('auto')
}

def fanCirculate() {
    setThermostatFanMode('circulate')
}

def setThermostatFanMode(mode) { 
	Map fanMap = [auto:0, on:1, circulate:2, followSchedule:3]   
	if (debugOutput) log.debug "setThermostatFanMode: $mode"
	deviceDataInit(null) 
	def fanMode = null
	
	device.data.FanMode = fanMap.find{ mode == it.key }?.value
	setStatus()
	
	if(device.data.SetStatus==1)
	{
	    sendEvent(name: 'thermostatFanMode', value: mode)    
	}
}


def setStatus() {

    device.data.SetStatus = 0

    login()
    if (debugOutput) log.debug "Honeywell TCC 'setStatus'"
    def today = new Date()

    def params = [
        uri: "https://${tccSite()}/portal/Device/SubmitControlScreenChanges",
        headers: [
            'Accept': 'application/json, text/javascript, */*; q=0.01', // */ comment
            'DNT': '1',
            'Accept-Encoding': 'gzip,deflate,sdch',
            'Cache-Control': 'max-age=0',
            'Accept-Language': 'en-US,en,q=0.8',
            'Connection': 'keep-alive',
            'Host': "${tccSite()}",
            'Referer': "https://${tccSite()}/portal/Device/Control/${settings.honeywelldevice}",
            'X-Requested-With': 'XMLHttpRequest',
            'User-Agent': 'Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/28.0.1500.95 Safari/537.36',
            'Cookie': device.data.cookiess
        ],
        body: [
            DeviceID: "${settings.honeywelldevice}",
            SystemSwitch: device.data.SystemSwitch,
            HeatSetpoint: device.data.HeatSetpoint,
            CoolSetpoint: device.data.CoolSetpoint,
            HeatNextPeriod: device.data.HeatNextPeriod,
            CoolNextPeriod: device.data.CoolNextPeriod,
            StatusHeat: device.data.StatusHeat,
            StatusCool: device.data.StatusCool,
            fanMode: device.data.FanMode,
            DisplayUnits: location.temperatureScale
        ],
	  timeout: 10
    ]

    if (debugOutput) log.debug "params = $params"
    try {
    	httpPost(params) {
    	    resp ->
    	        def setStatusResult = resp.data
    	    if (debugOutput) log.debug "Request was successful, $resp.status"
    	    device.data.SetStatus = 1
    	}
    } 
    catch (e) {
    	log.error "Something went wrong: $e"
    }

/*
    if (debugOutput) log.debug "params = $params"
    asynchttpPost("setStatusHandler", params) 
*/
}    


def setStatusHandler(resp, data) {
	//log.debug "data was passed successfully"
	//log.debug "status of post call is: ${resp.status}"

	if(resp.getStatus() == 408) {if (debugOutput) log.debug "TCC Request timed out, $resp.status"}
	if(resp.getStatus() == 200 || resp.getStatus() == 207) {
		def setStatusResult = resp.data
		if (debugOutput) log.debug "Request was successful, $resp.status"
		device.data.SetStatus = 1
	} else { if (descTextEnable) log.info "TCC setStatus failed" }
}


def getStatus() {
    if (debugOutput) log.debug "Honeywell TCC getStatus"
    if (debugOutput) log.debug "enable outside temps = $enableOutdoorTemps"
    def today = new Date()
    //if (debugOutput) log.debug "https://${tccSite()}/portal/Device/CheckDataSession/${settings.honeywelldevice}?_=$today.time"

    def params = [
        uri: "https://${tccSite()}/portal/Device/CheckDataSession/${settings.honeywelldevice}",
        headers: [
            'Accept': '*/*', // */ comment
            'DNT': '1',
            'Cache': 'false',
            'dataType': 'json',
            'Accept-Encoding': 'plain',
            'Cache-Control': 'max-age=0',
            'Accept-Language': 'en-US,en,q=0.8',
            'Connection': 'keep-alive',
            'Referer': "https://${tccSite()}/portal",
            'X-Requested-With': 'XMLHttpRequest',
            'User-Agent': 'Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/28.0.1500.95 Safari/537.36',
            'Cookie': device.data.cookiess
        ],
	  timeout: 10
    ]

    if (debugOutput) log.debug "sending getStatus request"
    asynchttpGet("getStatusHandler", params)
}

def getStatusHandler(resp, data) {
	if(resp.getStatus() == 200 || resp.getStatus() == 207) {
		def setStatusResult = parseJson(resp.data)
	
		if (debugOutput) log.debug "Request was successful, $resp.status"
	//	logInfo "data = $setStatusResult.data"
		if (debugOutput) log.debug "ld = $setStatusResult.latestData.uiData"
		
		def curTemp = setStatusResult.latestData.uiData.DispTemperature
		def switchPos = setStatusResult.latestData.uiData.SystemSwitchPosition
		def coolSetPoint = setStatusResult.latestData.uiData.CoolSetpoint
		def heatSetPoint = setStatusResult.latestData.uiData.HeatSetpoint
		def statusCool = setStatusResult.latestData.uiData.StatusCool
		def statusHeat = setStatusResult.latestData.uiData.StatusHeat
		def Boolean hasIndoorHumid= setStatusResult.latestData.uiData.IndoorHumiditySensorAvailable
		def curHumidity = setStatusResult.latestData.uiData.IndoorHumidity
		def Boolean hasOutdoorHumid = setStatusResult.latestData.uiData.OutdoorHumidityAvailable
		def Boolean hasOutdoorTemp = setStatusResult.latestData.uiData.OutdoorTemperatureAvailable
		def curOutdoorHumidity = setStatusResult.latestData.uiData.OutdoorHumidity
		def curOutdoorTemp = setStatusResult.latestData.uiData.OutdoorTemperature
		// EquipmentOutputStatus = 0 off 1 heating 2 cooling
		def equipmentStatus = setStatusResult.latestData.uiData.EquipmentOutputStatus	
		def holdTime = setStatusResult.latestData.uiData.TemporaryHoldUntilTime
		def vacationHold = setStatusResult.latestData.uiData.IsInVacationHoldMode
	
		state.heatLowerSetptLimit = setStatusResult.latestData.uiData.HeatLowerSetptLimit 
		state.heatUpperSetptLimit = setStatusResult.latestData.uiData.HeatUpperSetptLimit 
		state.coolLowerSetptLimit = setStatusResult.latestData.uiData.CoolLowerSetptLimit 
		state.coolUpperSetptLimit = setStatusResult.latestData.uiData.CoolUpperSetptLimit 
		
		def fanMode = setStatusResult.latestData.fanData.fanMode
		def fanIsRunning = setStatusResult.latestData.fanData.fanIsRunning

		if (debugOutput) log.debug "got holdTime = $holdTime"
		if (debugOutput) log.debug "got Vacation Hold = $vacationHold"
		
		if (holdTime != 0) {
		    if (debugOutput) log.debug "sending temporary hold"
		    sendEvent(name: 'followSchedule', value: "TemporaryHold")
		}
		
		if (vacationHold == true) {
		    if (debugOutput) log.debug "sending vacation hold"
		    sendEvent(name: 'followSchedule', value: "VacationHold")
		}
		
		if (vacationHold == false && holdTime == 0) {
		    if (debugOutput) log.debug "Sending following schedule"
		    sendEvent(name: 'followSchedule', value: "FollowingSchedule")
		}
		
		if (hasIndoorHumid == false) { curHumidity = 0 }
		
		//Operating State Section 
		//Set the operating state to off 
		// thermostatOperatingState - ENUM ["heating", "pending cool", "pending heat", "vent economizer", "idle", "cooling", "fan only"]
		
		// set fan and operating state
		def fanState = "idle"

		if (fanIsRunning) {
			fanState = "on";

		    def operatingState = [ 0: 'fan only', 1: 'heating', 2: 'cooling' ][equipmentStatus] ?: 'idle'
		}
		
		logInfo("Set Operating State to: $operatingState - Fan to $fanState")
		
		//fan mode 0=auto, 2=circ, 1=on, 3=followSched
		
		n = [ 0: 'auto', 2: 'circulate', 1: 'on', 3: 'followSchedule' ][fanMode]
		sendEvent(name: 'thermostatFanMode', value: n)

		n = [ 1: 'heat', 2: 'off', 3: 'cool', 5: 'auto' ][switchPos] ?: 'auto'
		sendEvent(name: 'temperature', value: curTemp, state: n, unit:device.data.unit)
		sendEvent(name: 'thermostatMode', value: n)
		lrM(n)

		
		//Send events 
		sendEvent(name: 'thermostatOperatingState', value: operatingState)
		sendEvent(name: 'fanOperatingState', value: fanState)
//		sendEvent(name: 'thermostatFanMode', value: fanMode)
//		sendEvent(name: 'thermostatMode', value: switchPos)
		sendEvent(name: 'coolingSetpoint', value: coolSetPoint, unit:device.data.unit)
		sendEvent(name: 'heatingSetpoint', value: heatSetPoint, unit:device.data.unit)
//		sendEvent(name: 'temperature', value: curTemp, state: switchPos, unit:device.data.unit)
		sendEvent(name: 'humidity', value: curHumidity as Integer, unit:"%")
		
		def now = new Date().format('MM/dd/yyyy h:mm a', location.timeZone)
		
		sendEvent(name: "lastUpdate", value: now, descriptionText: "Last Update: $now")
		
		if (enableOutdoorTemps == "Yes") {

		    if (hasOutdoorHumid) {
		        setOutdoorHumidity(curOutdoorHumidity)
		        sendEvent(name: 'outdoorHumidity', value: curOutdoorHumidity as Integer, unit:"%")
		    }
		
		    if (hasOutdoorTemp) {
		        setOutdoorTemperature(curOutdoorTemp)
		        sendEvent(name: 'outdoorTemperature', value: curOutdoorTemp as Integer, unit:device.data.unit)
		    }
		}
	} else { if (descTextEnable) log.info "TCC getStatus failed" }
}


def getHumidifierStatus()
{
	if (enableHumidity == 'No') return
	def params = [
        uri: "https://${tccSite()}/portal/Device/Menu/GetHumData/${settings.honeywelldevice}",
        headers: [
            'Accept': '*/*', // */ comment
            'DNT': '1',
            'dataType': 'json',
            'cache': 'false',
            'Accept-Encoding': 'plain',
            'Cache-Control': 'max-age=0',
            'Accept-Language': 'en-US,en,q=0.8',
            'Connection': 'keep-alive',
            'Host': 'rs.alarmnet.com',
            'Referer': 'https://${tccSite()}/portal/Menu/${settings.honeywelldevice}',
            'X-Requested-With': 'XMLHttpRequest',
            'User-Agent': 'Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/28.0.1500.95 Safari/537.36',
            'Cookie': device.data.cookiess
        ],
	  timeout: 10
    ]

    if (debugOutput) log.debug "sending gethumidStatus request: $params"
/*    asynchttpGet("getHumidStatusHandler", params)
}

def getHumidStatusHandler(resp, data) {
	if(resp.getStatus() == 200 || resp.getStatus() == 207) {
        if (debugOutput) log.debug "GetHumidity Request was successful, $resp.status"
*/
    try {
     httpGet(params) { response ->
        if (debugOutput) log.debug "GetHumidity Request was successful, $response.status"
        if (debugOutput) log.debug "response = $response.data"

        //  if (debugOutput) log.debug "ld = $response.data.latestData"
        //  if (debugOutput) log.debug "humdata = $response.data.latestData.humData"

        logInfo("lowerLimit: ${response.data.latestData.humData.lowerLimit}")        
        logInfo("upperLimit: ${response.data.humData.upperLimit}")        
        logInfo("SetPoint: ${response.data.humData.Setpoint}")        
        logInfo("DeviceId: ${response.data.humData.DeviceId}")        
        logInfo("IndoorHumidity: ${response.data.humData.IndoorHumidity}")        

     }
    } 
    catch (e) {
    	log.error "Something went wrong: $e"
    }

}

// Update lastRunningMode based on mode and operatingstate
def lrM(mode) {
	String lrm = getDataValue("lastRunningMode")
	if (mode.contains("auto") || mode.contains("off") && lrm != "heat") { updateDataValue("lastRunningMode", "heat") }
	 else { updateDataValue("lastRunningMode", mode) }
}

def api(method, args = [], success = {}) {}

// initialize the device values. Each method overwrites it's specific value
def deviceDataInit(val) {
    device.data.SystemSwitch = null 
    device.data.HeatSetpoint = null
    device.data.CoolSetpoint = null
    device.data.HeatNextPeriod = null
    device.data.CoolNextPeriod = null
    device.data.FanMode = null
    device.data.StatusHeat=val
    device.data.StatusCool=val
    device.data.unit = "°${location.temperatureScale}"

}

// Need to be logged in before this is called. So don't call this. Call api.
def doRequest(uri, args, type, success) {

}

def refresh() {
    device.data.unit = "°${location.temperatureScale}"
    if (debugOutput) log.debug "Honeywell TCC 'refresh', pollInterval: $pollInterval, units: = $device.data.unit"
    login()
    getHumidifierStatus()
    getStatus()
}

def login() {
    if (debugOutput) log.debug "Honeywell TCC 'login'"

    Map params = [
        uri: "https://${tccSite()}/portal/",
        headers: [
            'Content-Type': 'application/x-www-form-urlencoded',
            'Accept': 'application/json, text/javascript, */*; q=0.01', // */
            'Accept-Encoding': 'sdch',
            'Host': "${tccSite()}",
            'DNT': '1',
            'Origin': "https://${tccSite()}/portal/",
            'User-Agent': 'Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/28.0.1500.95 Safari/537.36'
        ],
        body: [timeOffset: '240', UserName: "${settings.username}", Password: "${settings.password}", RememberMe: 'false']
    ]

   // log.debug "Params: $params.headers $params.body"
    device.data.cookiess = ''

    try {
        httpPost(params) { 
            response ->
            if (debugOutput) log.debug "Request was successful, $response.status" // ${response.getHeaders()}"
            String allCookies = ""

       //     response.getHeaders('Set-Cookie').each { if (debugOutput) log.debug "---Set-Cookie: ${it.value}" }

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

                            if (expires < newDate) {
                                skipCookie = true
                                //if (debugOutput) log.debug "-skip cookie: $it.value"
                            } else {
                                //if (debugOutput) log.debug "+not skipping cookie: expires=$expires. now=$newDate. cookie: $it.value"
                            }

                        }
                    }
                } catch (e) {
                    if (debugOutput) log.debug "!error when checking expiration date: $e ($expiration) [$expireParts.length] {$it.value}"
                }

                allCookies = allCookies + it.value + ';'

                if (cookie != ".ASPXAUTH_TH_A=") {
                    if (it.value.split('=')[1].trim() != "") {
                        if (!skipCookie) {
                            if (debugOutput) log.debug "Adding cookie to collection: $cookie"
                            device.data.cookiess = device.data.cookiess + cookie + ';'
                        }
                    }
                }
            }
            //log.debug "cookies: $device.data.cookiess"
        }
    } catch (e) {
        log.warn "Something went wrong during login: $e"
    }
}

// Initialize after hub power cycle to force a poll cycle
def initialize() {
    logInfo "Initialize Poll"
    poll()
}


/* def isLoggedIn() {
    if(!device.data.auth) {
        if (debugOutput) log.debug "No device.data.auth"
        return false
    }

def now = new Date().getTime();
    return device.data.auth.expires_in > now
} */

def poll() {
    pollInterval = pollIntervals.toInteger()
    if (pollInterval) runIn(pollInterval, poll) 
    logInfo "in poll: (every $pollInterval seconds)"
    refresh()
}

def updated() {
    if (debugOutput) log.debug "in updated"
    pollInterval = pollIntervals.toInteger()
    if (debugOutput) log.debug "debug logging is: ${debugOutput == true}"
    if (descTextEnable) log.info "description logging is: ${descTextEnable == true}"
    unschedule()
    dbCleanUp()		// remove antique db entries created in older versions and no longer used.
    if (debugOutput) runIn(1800,logsOff)   
    if (setPermHold == "Permanent") { state.PermHold = 2 } else { state.PermHold = 1 }
    schedule("0 0 8 ? * FRI *", updateCheck)  // Cron schedule - How often to perform the update check - (This example is 8am every Friday)
    runIn(20, updateCheck) 
    if (debugOutput) log.debug "PermHold now = ${state.PermHold}"
    poll()
}

def logsOff(){
    if (descTextEnable) log.warn "debug logging disabled..."
    device.updateSetting("debugOutput",[value:"false",type:"bool"])
}


private logInfo(msg) {
	if (settings?.descTextEnable || settings?.descTextEnable == null) log.info "$msg"
}

private dbCleanUp() {
	// clean up state variables that are obsolete
	state.remove("tempOffset")
	state.remove("version")
	state.remove("Version")
	state.remove("sensorTemp")
	state.remove("author")
	state.remove("Copyright")
	state.remove("verUpdate")
	state.remove("verStatus")
	state.remove("Type")
	state.remove("DisplayUnits")
}


void setOutdoorTemperature(value){
    def cd = getChildDevice("${device.id}-Temperature Sensor")
	if (!cd) 
		{
		cd = addChildDevice("hubitat", "Generic Component Temperature Sensor", "${device.id}-Temperature Sensor", [name: "Outdoor Temperature", isComponent: true])	
		}
    String unit = "°${location.temperatureScale}"
    cd.parse([[name:"temperature", value:value, descriptionText:"${cd.displayName} is ${value}${unit}.", unit: unit]])
}

void setOutdoorHumidity(value){
    def cd = getChildDevice("${device.id}-Humidity Sensor")
	if (!cd) 
		{
		cd = addChildDevice("hubitat", "Generic Component Humidity Sensor", "${device.id}-Humidity Sensor", [name: "Outdoor Humidity", isComponent: true])	
		}
    cd.parse([[name:"humidity", value:value, descriptionText:"${cd.displayName} is ${value}%.", unit:"%"]])
}


// Check Version   ***** with great thanks and acknowledgment to Cobra (CobraVmax) for his original code ****
def updateCheck()
{    
	def paramsUD = [uri: "https://hubitatcommunity.github.io/HoneywellThermo-TCC/version2.json", timeout: 10  ]
	
 	asynchttpGet("updateCheckHandler", paramsUD) 
}

def updateCheckHandler(resp, data) {

	state.InternalName = "HoneywellThermoTCC_C"

	if (resp.getStatus() == 200 || resp.getStatus() == 207) {
		respUD = parseJson(resp.data)
		// log.warn " Version Checking - Response Data: $respUD"   // Troubleshooting Debug Code - Uncommenting this line should show the JSON response from your webserver 
		state.Copyright = "${thisCopyright} -- ${version()}"
		// uses reformattted 'version2.json' 
		def newVer = padVer(respUD.driver.(state.InternalName).ver)
		def currentVer = padVer(version())               
		state.UpdateInfo = (respUD.driver.(state.InternalName).updated)
            // log.debug "updateCheck: ${respUD.driver.(state.InternalName).ver}, $state.UpdateInfo, ${respUD.author}"

		switch(newVer) {
			case { it == "NLS"}:
			      state.Status = "<b>** This Driver is no longer supported by ${respUD.author}  **</b>"       
			      if (descTextEnable) log.warn "** This Driver is no longer supported by ${respUD.author} **"      
				break
			case { it > currentVer}:
			      state.Status = "<b>New Version Available (Version: ${respUD.driver.(state.InternalName).ver})</b>"
			      if (descTextEnable) log.warn "** There is a newer version of this Driver available  (Version: ${respUD.driver.(state.InternalName).ver}) **"
			      if (descTextEnable) log.warn "** $state.UpdateInfo **"
				break
			case { it < currentVer}:
			      state.Status = "<b>You are using a Test version of this Driver (Expecting: ${respUD.driver.(state.InternalName).ver})</b>"
			      if (descTextEnable) log.warn "You are using a Test version of this Driver (Expecting: ${respUD.driver.(state.InternalName).ver})"
				break
			default:
				state.Status = "Current"
				if (descTextEnable) log.info "You are using the current version of this driver"
				break
		}

		sendEvent(name: "chkUpdate", value: state.UpdateInfo)
		sendEvent(name: "chkStatus", value: state.Status)
      }
      else
      {
           log.error "Something went wrong: CHECK THE JSON FILE AND IT'S URI"
      }
}

/*
	padVer

	Version progression of 1.4.9 to 1.4.10 would mis-compare unless each column is padded into two-digits first.

*/ 
def padVer(ver) {
	def pad = ""
	ver.replaceAll( "[vV]", "" ).split( /\./ ).each { pad += it.padLeft( 2, '0' ) }
	return pad
}

def getThisCopyright(){"&copy; 2020 C Steele "}
