/**
 *  Radio Thermostat
 *
 *  Copyright 2015 Eric Chesters
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */


import groovy.json.JsonSlurper

preferences {
    input("confIpAddr", "string", title:"Thermostat IP Address",
        required:true, displayDuringSetup: true)
    input("confTcpPort", "number", title:"Thermostat TCP Port",
        defaultValue:"80", required:true, displayDuringSetup:true)
}



metadata {
	definition (name: "Radio Thermostat (sliders)", namespace: "sbairn", author: "Eric Chesters") {
        capability "Thermostat"
        capability "Temperature Measurement"
        capability "Sensor"
        capability "Refresh"
        capability "Polling"

        // Custom attributes
        attribute "fanState", "string"  // Fan operating state. Values: "on", "off"
        attribute "hold", "string"      // Target temperature Hold status. Values: "on", "off"

        // Custom commands
        command "holdOn"
        command "holdOff"
    }

    tiles {
        valueTile("temperature", "device.temperature") {
            state "temperature", label:'${currentValue}°', unit:"F",
                backgroundColors:[
                    [value: 31, color: "#153591"],
                    [value: 44, color: "#1e9cbb"],
                    [value: 59, color: "#90d2a7"],
                    [value: 74, color: "#44b621"],
                    [value: 84, color: "#f1d801"],
                    [value: 95, color: "#d04e00"],
                    [value: 96, color: "#bc2323"]
                ]
        }


		controlTile("heatSliderControl", "device.heatingSetpoint", "slider", height: 1, width: 2) {
			state "setHeatingSetpoint", action:"setHeatingSetpoint", range:"(40..80)", backgroundColor:"#d04e00"
		}

		valueTile("heatingSetpoint", "device.heatingSetpoint", decoration: "flat") {
			state "heat", label:'${currentValue}° heat', backgroundColor:"#ffffff"
		}

		controlTile("coolSliderControl", "device.coolingSetpoint", "slider", height: 1, width: 2) {
			state "setCoolingSetpoint", action:"setCoolingSetpoint", range:"(40..80)", backgroundColor: "#1e9cbb"
		}

		valueTile("coolingSetpoint", "device.coolingSetpoint", decoration: "flat") {
			state "cool", label:'${currentValue}° cool', backgroundColor:"#ffffff"
		}


        standardTile("operatingState", "device.thermostatOperatingState", decoration:"flat") {
            state "default", label:'[State]'
            state "idle", label:'', icon:"st.thermostat.heating-cooling-off"
            state "heating", label:'', icon:"st.thermostat.heating"
            state "cooling", label:'', icon:"st.thermostat.cooling"
        }

        standardTile("fanState", "device.fanState", decoration:"flat") {
            state "default", label:'[Fan State]'
            state "on", label:'', icon:"st.thermostat.fan-on"
            state "off", label:'', icon:"st.thermostat.fan-off"
        }

        standardTile("mode", "device.thermostatMode") {
            state "default", label:'[Mode]'
            state "off", label:'', icon:"st.thermostat.heating-cooling-off", backgroundColor:"#FFFFFF", action:"thermostat.heat"
            state "heat", label:'', icon:"st.thermostat.heat", backgroundColor:"#FFCC99", action:"thermostat.cool"
            state "cool", label:'', icon:"st.thermostat.cool", backgroundColor:"#99CCFF", action:"thermostat.off"

/*          state "cool", label:'', icon:"st.thermostat.cool", backgroundColor:"#99CCFF", action:"thermostat.auto"
            state "auto", label:'', icon:"st.thermostat.auto", backgroundColor:"#99FF99", action:"thermostat.off"
*/
        }

        standardTile("fanMode", "device.thermostatFanMode") {
            state "default", label:'[Fan Mode]'
            state "auto", label:'', icon:"st.thermostat.fan-auto", backgroundColor:"#A4FCA6", action:"thermostat.fanOn"
            state "on", label:'', icon:"st.thermostat.fan-on", backgroundColor:"#FAFCA4", action:"thermostat.fanAuto"
        }

        standardTile("hold", "device.hold") {
            state "default", label:'[Hold]'
            state "on", label:'Hold On', icon:"st.Weather.weather2", backgroundColor:"#FFDB94", action:"holdOff"
            state "off", label:'Hold Off', icon:"st.Weather.weather2", backgroundColor:"#FFFFFF", action:"holdOn"
        }

        standardTile("refresh", "device.thermostatMode", decoration:"flat") {
            state "default", icon:"st.secondary.refresh", action:"refresh.refresh"
        }

        main(["temperature"])

        details(["temperature", "operatingState", "fanState",
            "heatSliderControl", "heatingSetpoint",
            "coolSliderControl", "coolingSetpoint",
            "mode", "fanMode", "hold", "refresh"])
    }

    simulator {
        status "Temperature 72.0":      "simulator:true, temp:72.00"
        status "Cooling Setpoint 76.0": "simulator:true, t_cool:76.00"
        status "Heating Setpoint 68.0": "simulator:true, t_cool:68.00"
        status "Thermostat Mode Off":   "simulator:true, tmode:0"
        status "Thermostat Mode Heat":  "simulator:true, tmode:1"
        status "Thermostat Mode Cool":  "simulator:true, tmode:2"
        status "Thermostat Mode Auto":  "simulator:true, tmode:3"
        status "Fan Mode Auto":         "simulator:true, fmode:0"
        status "Fan Mode Circulate":    "simulator:true, fmode:1"
        status "Fan Mode On":           "simulator:true, fmode:2"
        status "State Off":             "simulator:true, tstate:0"
        status "State Heat":            "simulator:true, tstate:1"
        status "State Cool":            "simulator:true, tstate:2"
        status "Fan State Off":         "simulator:true, fstate:0"
        status "Fan State On":          "simulator:true, fstate:1"
        status "Hold Disabled":         "simulator:true, hold:0"
        status "Hold Enabled":          "simulator:true, hold:1"
    }
}

def parse(String message) {
    TRACE("parse(${message})")

    def msg = stringToMap(message)

    if (msg.headers) {
        // parse HTTP response headers
        def headers = new String(msg.headers.decodeBase64())
        def parsedHeaders = parseHttpHeaders(headers)
        TRACE("parsedHeaders: ${parsedHeaders}")
        if (parsedHeaders.status != 200) {
            log.error "Server error: ${parsedHeaders.reason}"
            return null
        }

        // parse HTTP response body
        if (!msg.body) {
            log.error "HTTP response has no body"
            return null
        }

        def body = new String(msg.body.decodeBase64())
        def slurper = new JsonSlurper()
        def tstat = slurper.parseText(body)

        return parseTstatData(tstat)
    } else if (msg.containsKey("simulator")) {
        // simulator input
        return parseTstatData(msg)
    }

    return null
}

// thermostat.setThermostatMode
def setThermostatMode(mode) {
    TRACE("setThermostatMode(${mode})")

    switch (mode) {
    case "off":             return off()
    case "heat":            return heat()
    case "cool":            return cool()
    case "auto":            return auto()
    case "emergency heat":  return emergencyHeat()
    }

    log.error "Invalid thermostat mode: \'${mode}\'"
}

// thermostat.off
def off() {
    TRACE("off()")

    if (device.currentValue("thermostatMode") == "off") {
        return null
    }

    sendEvent([name:"thermostatMode", value:"off"])
    return writeTstatValue('tmode', 0)
}

// thermostat.heat
def heat() {
    TRACE("heat()")

    if (device.currentValue("thermostatMode") == "heat") {
        return null
    }

    sendEvent([name:"thermostatMode", value:"heat"])
    return writeTstatValue('tmode', 1)
}

// thermostat.cool
def cool() {
    TRACE("cool()")

    if (device.currentValue("thermostatMode") == "cool") {
        return null
    }

    sendEvent([name:"thermostatMode", value:"cool"])
    return writeTstatValue('tmode', 2)
}

// thermostat.auto
def auto() {
    TRACE("auto()")

    if (device.currentValue("thermostatMode") == "auto") {
        return null
    }

    sendEvent([name:"thermostatMode", value:"auto"])
    return writeTstatValue('tmode', 3)
}

// thermostat.emergencyHeat
def emergencyHeat() {
    TRACE("emergencyHeat()")
    log.warn "'emergency heat' mode is not supported"
    return null
}

// thermostat.setThermostatFanMode
def setThermostatFanMode(fanMode) {
    TRACE("setThermostatFanMode(${fanMode})")

    switch (fanMode) {
    case "auto":        return fanAuto()
    case "circulate":   return fanCirculate()
    case "on":          return fanOn()
    }

    log.error "Invalid fan mode: \'${fanMode}\'"
}

// thermostat.fanAuto
def fanAuto() {
    TRACE("fanAuto()")

    if (device.currentValue("thermostatFanMode") == "auto") {
        return null
    }

    sendEvent([name:"thermostatFanMode", value:"auto"])
    return writeTstatValue('fmode', 0)
}

// thermostat.fanCirculate
def fanCirculate() {
    TRACE("fanCirculate()")
    log.warn "Fan 'Circulate' mode is not supported"
    return null
}

// thermostat.fanOn
def fanOn() {
    TRACE("fanOn()")

    if (device.currentValue("thermostatFanMode") == "on") {
        return null
    }

    sendEvent([name:"thermostatFanMode", value:"on"])
    return writeTstatValue('fmode', 2)
}

// thermostat.setHeatingSetpoint
def setHeatingSetpoint(tempHeat) {
    TRACE("setHeatingSetpoint(${tempHeat})")

    def ev = [
        name:   "heatingSetpoint",
        value:  tempHeat,
        unit:   getTemperatureScale(),
    ]

    sendEvent(ev)

    if (getTemperatureScale() == "C") {
        tempHeat = temperatureCtoF(tempHeat)
    }

    return writeTstatValue('it_heat', tempHeat)
}

// thermostat.setCoolingSetpoint
def setCoolingSetpoint(tempCool) {
    TRACE("setCoolingSetpoint(${tempCool})")

    def ev = [
        name:   "coolingSetpoint",
        value:  tempCool,
        unit:   getTemperatureScale(),
    ]

    sendEvent(ev)

    if (getTemperatureScale() == "C") {
        tempCool = temperatureCtoF(tempCool)
    }

    return writeTstatValue('it_cool', tempCool)
}


def holdOn() {
    TRACE("holdOn()")

    if (device.currentValue("hold") == "on") {
        return null
    }

    sendEvent([name:"hold", value:"on"])
    writeTstatValue("hold", 1)
}

def holdOff() {
    TRACE("holdOff()")

    if (device.currentValue("hold") == "off") {
        return null
    }

    sendEvent([name:"hold", value:"off"])
    writeTstatValue("hold", 0)
}

// polling.poll 
def poll() {
    TRACE("poll()")
    return refresh()
}

// refresh.refresh
def refresh() {
    TRACE("refresh()")
    STATE()

    setNetworkId(confIpAddr, confTcpPort)
    return apiGet("/tstat")
}

// Sets device Network ID in 'AAAAAAAA:PPPP' format
private String setNetworkId(ipaddr, port) { 
    TRACE("setNetworkId(${ipaddr}, ${port})")

    def hexIp = ipaddr.tokenize('.').collect {
        String.format('%02X', it.toInteger())
    }.join()

    def hexPort = String.format('%04X', port.toInteger())
    device.deviceNetworkId = "${hexIp}:${hexPort}"
    log.debug "device.deviceNetworkId = ${device.deviceNetworkId}"
}

private apiGet(String path) {
    TRACE("apiGet(${path})")

    def headers = [
        HOST:       "${confIpAddr}:${confTcpPort}",
        Accept:     "*/*"
    ]

    def httpRequest = [
        method:     'GET',
        path:       path,
        headers:    headers
    ]

    return new physicalgraph.device.HubAction(httpRequest)
}

private apiPost(String path, data) {
    TRACE("apiPost(${path}, ${data})")

    def headers = [
        HOST:       "${confIpAddr}:${confTcpPort}",
        Accept:     "*/*"
    ]

    def httpRequest = [
        method:     'POST',
        path:       path,
        headers:    headers,
        body:       data
    ]

    return new physicalgraph.device.HubAction(httpRequest)
}

private def writeTstatValue(name, value) {
    TRACE("writeTstatValue(${name}, ${value})")

    setNetworkId(confIpAddr, confTcpPort)

    def json = "{\"${name}\": ${value}}"
    def hubActions = [
        apiPost("/tstat", json),
        delayHubAction(2000),
        apiGet("/tstat")
    ]

    return hubActions
}

private def delayHubAction(ms) {
    return new physicalgraph.device.HubAction("delay ${ms}")
}

private parseHttpHeaders(String headers) {
    def lines = headers.readLines()
    def status = lines.remove(0).split()

    def result = [
        protocol:   status[0],
        status:     status[1].toInteger(),
        reason:     status[2]
    ]

    return result
}

private def parseTstatData(Map tstat) {
    TRACE("parseTstatData(${tstat})")

    def events = []
    if (tstat.containsKey("error_msg")) {
        log.error "Thermostat error: ${tstat.error_msg}"
        return null
    }

    if (tstat.containsKey("success")) {
        // this is POST response - ignore
        return null
    }

    if (tstat.containsKey("temp")) {
        //Float temp = tstat.temp.toFloat()
        def ev = [
            name:   "temperature",
            value:  scaleTemperature(tstat.temp.toFloat()),
            unit:   getTemperatureScale(),
        ]

        events << createEvent(ev)
    }

    if (tstat.containsKey("t_cool")) {
        def ev = [
            name:   "coolingSetpoint",
            value:  scaleTemperature(tstat.t_cool.toFloat()),
            unit:   getTemperatureScale(),
        ]

        events << createEvent(ev)
    }

    if (tstat.containsKey("t_heat")) {
        def ev = [
            name:   "heatingSetpoint",
            value:  scaleTemperature(tstat.t_heat.toFloat()),
            unit:   getTemperatureScale(),
        ]

        events << createEvent(ev)
    }

    if (tstat.containsKey("tstate")) {
        def value = parseThermostatState(tstat.tstate)
        if (device.currentState("thermostatOperatingState")?.value != value) {
            def ev = [
                name:   "thermostatOperatingState",
                value:  value
            ]

            events << createEvent(ev)
        }
    }

    if (tstat.containsKey("fstate")) {
        def value = parseFanState(tstat.fstate)
        if (device.currentState("fanState")?.value != value) {
            def ev = [
                name:   "fanState",
                value:  value
            ]

            events << createEvent(ev)
        }
    }

    if (tstat.containsKey("tmode")) {
        def value = parseThermostatMode(tstat.tmode)
        if (device.currentState("thermostatMode")?.value != value) {
            def ev = [
                name:   "thermostatMode",
                value:  value
            ]

            events << createEvent(ev)
        }
    }

    if (tstat.containsKey("fmode")) {
        def value = parseFanMode(tstat.fmode)
        if (device.currentState("thermostatFanMode")?.value != value) {
            def ev = [
                name:   "thermostatFanMode",
                value:  value
            ]

            events << createEvent(ev)
        }
    }

    if (tstat.containsKey("hold")) {
        def value = parseThermostatHold(tstat.hold)
        if (device.currentState("hold")?.value != value) {
            def ev = [
                name:   "hold",
                value:  value
            ]

            events << createEvent(ev)
        }
    }

    TRACE("events: ${events}")
    return events
}

private def parseThermostatState(val) {
    def values = [
        "idle",     // 0
        "heating",  // 1
        "cooling"   // 2
    ]

    return values[val.toInteger()]
}

private def parseFanState(val) {
    def values = [
        "off",      // 0
        "on"        // 1
    ]

    return values[val.toInteger()]
}

private def parseThermostatMode(val) {
    def values = [
        "off",      // 0
        "heat",     // 1
        "cool",     // 2
        "auto"      // 3
    ]

    return values[val.toInteger()]
}

private def parseFanMode(val) {
    def values = [
        "auto",     // 0
        "circulate",// 1 (not supported by CT30)
        "on"        // 2
    ]

    return values[val.toInteger()]
}

private def parseThermostatHold(val) {
    def values = [
        "off",      // 0
        "on"        // 1
    ]

    return values[val.toInteger()]
}

private def scaleTemperature(Float temp) {
    if (getTemperatureScale() == "C") {
        return temperatureFtoC(temp)
    }

    return temp.round(1)
}

private def temperatureCtoF(Float tempC) {
    Float t = (tempC * 1.8) + 32
    return t.round(1)
}

private def temperatureFtoC(Float tempF) {
    Float t = (tempF - 32) / 1.8
    return t.round(1)
}

private def TRACE(message) {
    //log.debug message
}

private def STATE() {
    log.debug "deviceNetworkId : ${device.deviceNetworkId}"
    log.debug "temperature : ${device.currentValue("temperature")}"
    log.debug "heatingSetpoint : ${device.currentValue("heatingSetpoint")}"
    log.debug "coolingSetpoint : ${device.currentValue("coolingSetpoint")}"
    log.debug "thermostatMode : ${device.currentValue("thermostatMode")}"
    log.debug "thermostatFanMode : ${device.currentValue("thermostatFanMode")}"
    log.debug "thermostatOperatingState : ${device.currentValue("thermostatOperatingState")}"
    log.debug "fanState : ${device.currentValue("fanState")}"
    log.debug "hold : ${device.currentValue("hold")}"
}


