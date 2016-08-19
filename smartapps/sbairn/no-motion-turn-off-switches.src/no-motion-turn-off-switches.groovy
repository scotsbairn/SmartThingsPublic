/**
 *  Good Night Switches
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
    name: "No Motion Turn Off Switches",
    namespace: "sbairn",
    author: "Eric Chesters",
    description: "Smart turn things off at night",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
    section("Enabled") {
        input(name: "isEnabledBool", type: "bool", title: "Enabled ?")
    }    
    section("Time window") {
        input "timeStart", "time", title: "Start time"
        input "timeEnd", "time", title: "End time"
    }
    section("Control these switches..."){
        input "turnOff", "capability.switch", multiple: true
    }
    section("Turn off when there's no movement..."){
        input "motionSensors", "capability.motionSensor", title: "Where?", multiple: true
    }
    section("For..."){
        input "delayMinutes", "number", title: "Minutes?"
    }     
}

def installed() {
	log.debug "Installed with settings: ${settings}"
	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"
	initialize()
}

def initialize() {
    state.turnOffAfter=null

    Date now=new Date()        
    state.turnOffAfter=now.getTime()+(60*1000*delayMinutes)         
    state.lastCheck=now.getTime();
    
    initEvents()

    doCheck()
}

def reInitSchedule(evt) {
    initEvents()

    doCheck()
}

def initEvents() {
    log.debug "initEvents:"
	unsubscribe()
    unschedule()

    schedule("0 */5 * * * ?", doCheck)
    subscribe(motionSensors, "motion", motionHandler)

    subscribe(location, "sunset", reInitSchedule)
    subscribe(location, "sunrise", reInitSchedule)
}

def motionHandler(evt) {
    Date now=new Date()        

    state.turnOffAfter=now.getTime()+(60*1000*delayMinutes)         
    log.debug("motionHandler: set turnOffAfter set to $state.turnOffAfter")
    
    if(state.lastCheck==null) {
        state.lastCheck=now.getTime()
    } else {
        if(now.getTime() > (state.lastCheck+(60*1000*15))) {
            log.debug("motionHandler: last check not seen within 15 minutes, init the events")
            initEvents()
        }
    }
}


def doCheck() {
    def now = new Date()
    state.lastCheck=now.getTime();

    if(!isEnabledBool) {
        log.debug("doCheck: disabled, skipping doCheck")
        return
    }

    log.debug("doCheck: $state")

    def start = timeToday(timeStart, location.timeZone)
    def end = timeTodayAfter(timeStart,timeEnd, location.timeZone)

    if(now.after(start) && now.before(end)) {
        def switchesOn=turnOff.currentValue("switch").contains("on")    

        log.debug("doCheck: in window: state $switchesOn")

        if(switchesOn) {
            def motionState=motionSensors.currentValue("motion")
            def motionActive=motionState.contains("active")

            log.debug "doCheck: motion: $motionState $motionActive"

            if(motionActive) {
                log.debug "doCheck: motion detected, do nothing"
            } else {
                if((state.turnOffAfter == null) || (now.getTime() > state.turnOffAfter)) {
                    log.debug "doCheck: no motion detected and after the turnOffAfter time, turn the switches off"
                    turnOff.off()
                } else {
                    log.debug "doCheck: not reached turnOffAfter time yet"
                }
            }
        } else {
            log.debug "doCheck: all switches are turned off, we are done for now"
        }
    } else {
        log.debug("doCheck: out of window, don't check")        
    }
}
