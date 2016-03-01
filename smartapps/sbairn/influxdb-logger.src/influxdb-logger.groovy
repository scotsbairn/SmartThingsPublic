/**
 *  InfluxDV Logger
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
definition(
    name: "InfluxDB Logger",
    namespace: "sbairn",
    author: "Eric Chesters",
    description: "Log information to InfluxDB",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
	section("Logger") {
		input "logger", "capability.switch", title: "Logger"
	}
	section("Temperatures") {
		input "mpTemp",  "capability.temperatureMeasurement", title: "Thermometer(s)", multiple: true, required: false
	}
    section("Power Consumption") {
    	input "mpPower", "capability.powerMeter", title: "Powermeter(s)", multiple: true, required: false
	}
    section("Battery %") {
    	input "mpBattery", "capability.battery", title: "Batteries", multiple: true, required: false
	}
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
    unschedule()

	initialize()
}

def initialize() {
    if(mpTemp!=null) {
        subscribe(mpTemp, "temperature", "handleTempEvent")
    }
    if(mpPower!=null) {
        subscribe(mpTemp, "power", "handlePowerEvent")
    }
    doLogging(true)
}

def handleTempEvent(evt) {
	log.debug "Temp event $evt"
    doLogging(false)
}

def handlePowerEvent(evt) {
	log.debug "Power event $evt"
    doLogging(false)
}

def doLogging(forceLog) { 
    def nowTime=now()
    
    if(!forceLog) {
        // do we have a pending update at some point in the future ?
        if(state.nextUpdateScheduledAt!=null && nowTime<state.nextUpdateScheduledAt) {
            log.debug "nextUpdate pending, skipping this update"
            log.debug "new state: $state"
            return
        }

        if(state.lastUpdateAt!=null) {
            // earliest we should do an update
            def nextRunTime=state.lastUpdateAt+(5*60*1000)

            // are we too early to do the update ?
            if(nowTime<nextRunTime) {
                state.nextUpdateScheduledAt=nextRunTime

                runIn(((nextRunTime-nowTime)/1000)+5, doLogging)

                log.debug "update attempted too early"
                //log.debug "new state: $state"
                return        
            }
        }
    } 

    // clear nextUpdateAt information
    state.nextUpdateScheduledAt=0

    // record current update event
    state.lastUpdateAt=nowTime

    // make sure we clear any scheduled update to avoid a race between a scheduled update and an event based update
    unschedule("doLogging")

    log.debug "prepare logging update"

    def dataStr=""

    boolean first=true    
    if(mpTemp!=null) {
        for(int i=0;i<mpTemp.size();i++) {
            def sensor=mpTemp[i]
            def temp=sensor.currentValue('temperature')
            if(temp!=null) {
                //log.debug "temp sensor $sensor = $temp"

                def sensorDataStr="\"name\": \"${sensor}\", \"columns\": [\"temp\"], \"points\": [[${temp}]]"
                //log.debug "sensorDataStr: ${sensorDataStr}"

                if(!first) {
                    dataStr+=","
                }
                dataStr+="{${sensorDataStr}}"

                first=false
            }
        }
    }

    if(mpPower!=null) {
        for(int i=0;i<mpPower.size();i++) {
            def sensor=mpPower[i]
            def power=sensor.currentValue('power')
            if(power!=null) {

                def sensorDataStr="\"name\": \"${sensor}\", \"columns\": [\"power\"], \"points\": [[${power}]]"

                if(!first) {
                    dataStr+=","
                }
                dataStr+="{${sensorDataStr}}"

                first=false
            }
        }
    }

    if(mpBattery!=null) {
        log.debug("log battery power")
        for(int i=0;i<mpBattery.size();i++) {
            def sensor=mpBattery[i]
            def battery=sensor.currentValue('battery')
            if(battery!=null) {

                def sensorDataStr="\"name\": \"${sensor}\", \"columns\": [\"battery\"], \"points\": [[${battery}]]"

                if(!first) {
                    dataStr+=","
                }
                dataStr+="{${sensorDataStr}}"

                first=false
            } else {
                log.debug("battery returned null")
            }
        }
    }

    if(dataStr!="") {
        def pushStr="["+dataStr+"]"
        log.debug "push data via logger device: ${pushStr}"
        return logger.pushData(pushStr)
    }
}

