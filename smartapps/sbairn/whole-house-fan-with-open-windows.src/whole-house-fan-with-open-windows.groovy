/**
 *  Whole House Fan
 *
 *  Copyright 2014 Ryan Bennett
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
    name: "Whole House Fan with Open Windows",
    namespace: "sbairn",
    author: "sbairn@gmail.com",
    description: "Toggle a whole house fan (switch) when: Outside is cooler than inside + offset, Inside is above x temp, Thermostat is off, windows/doors open. Stolen almost entirely from Ryan Bennett who stole from the original Whole House Fan app by Brian Steere.",
    category: "Green Living",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Developers/whole-house-fan.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Developers/whole-house-fan%402x.png"
)


preferences {
    section("Enable") {
        input "enable", "capability.switch", title: "Enable WHF"
    }

	section("Targets") {
        input "targetTemp", "number", title: "Target Indoor Temperature" 
        input "fanBackoff", "number", title: "Start to turn fans off as we get within range of the target indoor temp" 
    }

	section("Outdoor") {
		input "outTemp", "capability.temperatureMeasurement", title: "Outdoor Thermometer"
        input "offsetTemp", "number", title: "Outdoor Temperature Offset (treated as -ve number)"  
	}
    
    section("Indoor") {
    	input "thermostat", "capability.thermostat", title: "Thermostat" 
    	input "inTemp", "capability.temperatureMeasurement", title: "Indoor Thermometer" 
        input "windows", "capability.contactSensor", title: "Open Windows or Doors", multiple: true, required: false
        input "fans", "capability.switch", title: "Vent Fan", multiple: true
    }  
}


def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() {
	state.fanRunning = 0
    state.windowsOpen = false
    state.random = new Random()

    subscribe(enable, "switch.on", "enableOn");
    subscribe(enable, "switch.off", "enableOff");
    subscribe(outTemp, "temperature", "handleTempEvent");
    subscribe(inTemp, "temperature", "handleTempEvent");
    subscribe(thermostat, "thermostatOperatingState", "handleTempEvent");    
    subscribe(windows, "contact", "handleWindowEvent"); 
    
    updateWindowInfo()
    checkFans(true)
}

def enableOn(evt) {
    log.debug "enable turned on, checkFans()"
    thermostat.setThermostatMode("off")
    checkFans(true);
}

def enableOff(evt) {
    log.debug "enable turned off, turn off all fans"
    fans.off();
    thermostat.setThermostatFanMode("auto")
}

def handleWindowEvent(evt) {
	log.debug "Window event: triggered"
	updateWindowInfo()
	checkFans(true)
}

def updateWindowInfo() {
	if(windows) {
	    def windowState=windows.latestValue("contact")
	    state.windowsOpen=windowState.contains("open")
    
   		log.debug "Window event: check $windows $windowState $state.windowsOpen"
    } else {
        state.windowsOpen=false
    	log.debug "Window event: no windows defined"
    }
}


def handleTempEvent(evt) {
	log.debug "Temp event $evt"
    checkFans(false)
}


def checkFans(forceCheck) {
    if(fans.size()==0) {
        log.debug "checkFans: no fans defined"
        return
    }

    // move this up here so we get a debug log of temps even when not enabled
    def outsideTemp = settings.outTemp.currentValue('temperature')
    def insideTemp = settings.inTemp.currentValue('temperature')

    // change 11/6, needs to be checked, used to be thermostatOperatingState
    def thermostatMode = settings.thermostat.currentValue('thermostatMode')

    log.debug "Inside: $insideTemp, Outside: $outsideTemp, Thermostat: $thermostatMode, Windows: $state.windowsOpen"

	def fanState=fans.currentValue("switch")
	def fansOn=fanState.count("on")
	log.debug "checkFans: check $fans $fanState $fansOn"

    // not enabled ?
    if(enable.currentValue("switch")!="on") {
	   	if(fansOn!=0) {
        	log.debug "checkFans: force fans off, whf disabled"
	        fans.off()
        }

        return
    }

    def nowTime=now()

    
    if(!forceCheck) {
        // dampen the control loop a bit

        // do we have a pending check at some point in the future ?
        if(state.nextCheckScheduledAt!=null && nowTime<state.nextCheckScheduledAt) {
            log.debug "nextCheck pending, skipping this check"
            log.debug "new state: $state"
            return
        }

        if(state.lastCheckAt!=null) {

            // earliest we should re check the fan status
            def nextRunTime=state.lastCheckAt+(2*60*1000)

            // are we too early to do the check ?
            if(nowTime<nextRunTime) {
                state.nextCheckScheduledAt=nextRunTime

                runIn(((nextRunTime-nowTime)/1000)+5, checkFans)

                log.debug "check attempted too early"
                log.debug "new state: $state"
                return        
            }
        }
    } 

    // clear nextCheckAt information
    state.nextCheckScheduledAt=0

    // record current check event
    state.lastCheckAt=nowTime

    // make sure we clear any scheduled check to avoid a race between a scheduled check and an event based check
    unschedule("checkFans")

    def shouldRunFans = true

    if((thermostatMode!=null) && (thermostatMode != 'idle') && (thermostatMode != 'off')) {
	   	log.debug "checkFans: Not running due to thermostat mode"
    	shouldRunFans = false
    }
    
    if(insideTemp && outsideTemp && offsetTemp) {
		if(insideTemp <= outsideTemp - offsetTemp) {
    		log.debug "checkFans: Not running due to insideTemp <= outdoorTemp + offsetTemp"
    		shouldRunFans = false
	    }
    } else {
    	log.debug "checkFans: Not running due to no inside/outside temp data being available"
        shouldRunFans = false
    }
    
    if(insideTemp && settings.targetTemp) {
	    if(insideTemp <= settings.targetTemp) {
    		log.debug "checkFans: Not running due to insideTemp <= targetTemp"
    		shouldRunFans = false
        }
    } else {
    	log.debug "checkFans: Not running due to no inside/min temp data being available"
        shouldRunFans = false
    }

	if(!state.windowsOpen) {
   		log.debug "checkFans: Not running due to windows closed"
   		shouldRunFans = false
   	}

	if(shouldRunFans) {
	    def fansNeeded = fans.size()

        def degreesToGo=insideTemp-settings.targetTemp        


        if(degreesToGo<settings.fanBackoff) {
            fansNeeded=Math.round(fans.size() * 0.5)
        }

        log.debug "checkFans: fans needed $fansNeeded degreesToGo $degreesToGo"

        def rn=new Random()
        if(fansOn==0) {
            thermostat.setThermostatFanMode("on")
        }
		if(fansNeeded>fansOn) {
            if(fansNeeded > fans.size()) {
                fans.on()
            } else {
                while (fansNeeded>fansOn) {
                    def o=rn.nextInt(fans.size())
                    def found=false
                    for(int i=0; !found && i<fans.size();i++) {
                        def j=(o+i)%fans.size()
                        def fan=fans[j]
                        if(fan.currentValue("switch") == "off") {
                            fan.on()
                            found=true
                            log.debug("checkFans: turn on fan $fan")
                        }
                    }
                    fansOn++
                }
            }                
        } else if (fansNeeded<fansOn) { 
            if(fansNeeded==0) {
                fans.off()
                thermostat.setThermostatFanMode("auto")
            } else {
                while (fansNeeded<fansOn) {
                    def o=rn.nextInt(fans.size())
                    def found=false
                    for(int i=0; !found && i<fans.size();i++) {
                        def j=(o+i)%fans.size()
                        def fan=fans[j]

                        if(fan.currentValue("switch") == "on") {
                            fan.off()
                            found=true
                            log.debug("checkFans: turn off fan $fan")
                        }
                    }
                    fansOn--
                }
            }
            
        }

    	  
    } else {
    	log.debug "checkFans: make sure fans are all off"
        
		if(fansOn) {
        	fans.off()
            thermostat.setThermostatFanMode("auto")
        }
    }	

    log.debug "new state: $state"
}