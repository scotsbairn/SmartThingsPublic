/**
 * 
 * InfluxDB Logger device with off/on switch
 *
 */
metadata {

    preferences {
        input("confIpAddr", "string", title:"Logger IP Address",
            required:true, displayDuringSetup: true)
        input("confTcpPort", "number", title:"Logger TCP Port",
            defaultValue:"8086", required:true, displayDuringSetup:true)
        input("confPath", "string", title:"Logger Path",
            required:true, displayDuringSetup: true)
    }


	// Automatically generated. Make future change here.
	definition (name: "InfluxDBLogger", namespace: "sbairn", author: "Eric Chesters") {
		capability "Actuator"
		capability "Switch"
		capability "Sensor"

		command "pushData", ["string"]
	}

	// simulator metadata
	simulator {
	}

	// UI tile definitions
	tiles {
		standardTile("button", "device.switch", width: 2, height: 2, canChangeIcon: true) {
			state "off", label: 'Off', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff", nextState: "on"
			state "on", label: 'On', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#79b821", nextState: "off"
		}
		main "button"
		details "button"
	}
}

def parse(String description) {
}

def on() {
    log.debug("turn logging on")
    sendEvent(name: "switch", value: "on")
    return loggerPost("[{\"name\":\"logger\",\"columns\":[\"enable\"],\"points\":[[\"on\"]]}]")
}

def off() {
    log.debug("turn logging off")
	sendEvent(name: "switch", value: "off")
    return loggerPost("[{\"name\":\"logger\",\"columns\":[\"enable\"],\"points\":[[\"off\"]]}]")
}



private String setNetworkId(ipaddr, port) { 
    log.debug("setNetworkId(${ipaddr}, ${port})")

    def hexIp = ipaddr.tokenize('.').collect {
        String.format('%02X', it.toInteger())
    }.join()

    def hexPort = String.format('%04X', port.toInteger())
    device.deviceNetworkId = "${hexIp}:${hexPort}"
    log.debug "device.deviceNetworkId = ${device.deviceNetworkId}"
}

private loggerPost(data) {
    log.debug("loggerPost(${data})")

    setNetworkId(confIpAddr, confTcpPort)

    def headers = [
        HOST:       "${confIpAddr}:${confTcpPort}",
        Accept:     "*/*"
    ]

    def httpRequest = [
        method:     'POST',
        path:       confPath,
        headers:    headers,
        body:       data
    ]

    log.debug("create new msgs to logger")
    return new physicalgraph.device.HubAction(httpRequest)
}

def pushData(dataStr) {
    def o=device.currentValue("switch")
    log.debug("current status: ${o}")
    if(o == "on") {
        return loggerPost(dataStr)
    }
}


