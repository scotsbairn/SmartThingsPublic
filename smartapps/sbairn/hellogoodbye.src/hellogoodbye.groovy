/**
 *
 * Heading Home 
 *
 * Author: Eric Chesters
 */


definition(
  	name: "HelloGoodbye",
  	namespace: "sbairn",
	author: "sbairn",
	description: "Does various things when you arrive or leave home.",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/light_motion-outlet-luminance.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/light_motion-outlet-luminance@2x.png"
)

preferences {

    page(name: "rootPage")
    page(name: "helloDoorsPage")
    page(name: "goodbyeDoorsPage")
    page(name: "routinesPage")
}

def rootPage() {

    dynamicPage(name: "rootPage", install: true, uninstall: true) {
        section("Enabled"){
            input(name: "isEnabledBool", type: "bool", title: "Enabled ?")
        } 
        section("Hello Actions"){
            input "helloSwitches", "capability.switch", title: "Turn on", multiple: true, required: false
            input "helloIfDarkSwitches", "capability.switch", title: "Turn on if dark", multiple: true, required: false
            input "helloHeatTargetTemp", "number", title: "Heat Target Temperature", required: false 
            input "helloCoolTargetTemp", "number", title: "Cool Target Temperature", required: false 
            input name: "hellonDoorLocks", title: "Number of door locks", type: "number", submitOnChange: true
            href(name: "toHelloDoorsPage", page: "helloDoorsPage", title: "Doors to unlock")
            input "helloDoorsToOpen", "capability.doorControl", title: "Doors to open", required: false, multiple: true
        }
        section("Goodbye Actions"){
            input "goodbyeSwitches", "capability.switch", title: "Turn off", multiple: true, required: false
            input "goodbyeIfLowPowerSwitches", "capability.switch", title: "Turn off if low power", multiple: true, required: false
            input "goodbyeLowPowerThreshold", "number", title: "Low power threshold", required: true 
            input "goodbyeHeatTargetTemp", "number", title: "Heat Target Temperature", required: false 
            input "goodbyeCoolTargetTemp", "number", title: "Cool Target Temperature", required: false
            input name: "goodbyenDoorLocks", title: "Number of door locks", type: "number", submitOnChange: true
            href(name: "toGoodbyeDoorsPage", page: "goodbyeDoorsPage", title: "Doors to lock")
            input "goodbyeDoorsToClose", "capability.doorControl", title: "Doors to close", required: false, multiple: true
        }

        section("Thermostat"){
            input "thermostat", "capability.thermostat", title: "Thermostat", multiple: false
        }
 
        section("Presence") {
            input "presence", "capability.presenceSensor", title: "Who to track", required: true, multiple: false
        }

        section("Trigger Routines") {
            href(name: "toRoutinesPage", page: "routinesPage", title: "Select routines")            
        }
    }
}

def helloDoorsPage() {
    dynamicPage(name: "helloDoorsPage", title: "Doors to unlock", uninstall: false, install: false) {
        if(hellonDoorLocks) {
            (1..hellonDoorLocks).each { n->
                section("Door $n") {
                    input(name: "helloDoorLock${n}", type: "capability.lock", title: "Door Lock", required: false)
                }
            }
        }
    }
}

def goodbyeDoorsPage() {
    dynamicPage(name: "goodbyeDoorsPage", title: "Doors to lock if closed", uninstall: false, install: false) {
        if(goodbyenDoorLocks) {
            (1..goodbyenDoorLocks).each { n->
                section("Door $n") {
                    input(name: "goodbyeDoorLock${n}", type: "capability.lock", title: "Door Lock", required: false)
                    input(name: "goodbyeDoorSensor${n}", type: "capability.contactSensor", title: "Open/Close sensor", required: false)
                }
            }
        }
    }
}


def routinesPage() {
    dynamicPage(name: "routinesPage", title: "Trigger Routines", install: true, uninstall: true) {
        // get the available actions
        def actions = location.helloHome?.getPhrases()*.label
        if (actions) {
            // sort them alphabetically
            actions.sort()
            section("Routines to trigger on") {
                // use the actions as the options for an enum input
                input "routineHeadingHome", "enum", title: "Heading Home", options: actions
                input "routineHello", "enum", title: "I'm Home", options: actions
                input "routineLeaving", "enum", title: "Leaving", options: actions
                input "routineGoodbye", "enum", title: "Goodbye", options: actions
            }
        }
    }
}


def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    state.currentState="idle"

    if(presence!=null) {
        subscribe(presence, "presence", presenceEvent)
    }

    subscribe(location, "routineExecuted", routineChanged)

    subscribe(app, appIdle)
}

def appIdle(evt) {
    log.debug("changing state to idle")
    state.currentState="idle";
    sendNotificationEvent("HelloGoodbye: going idle")
}

def doHello() {
    def errMsg=""

    sendNotificationEvent("HelloGoodbye: Hello")

    // turn on the unconditional switches
    helloSwitches?.on()

    // check if it is dark
    if(dark()) {
        log.debug("is dark, turn on ifDark switches")
        helloIfDarkSwitches?.on()
    }

    log.debug("number doors to lock: $hellonDoorLocks")
    if(hellonDoorLocks!=null && hellonDoorLocks>0) {
        (1..hellonDoorLocks).each { n->
            def door=settings."helloDoorLock${n}"
            log.debug("consider door: $door")

            if(door!=null) {
                if(door.currentValue('lock')=='locked') {
                    log.debug("unlocking $door")
                    door.unlock()
                } else {
                    log.debug("door $door already unlocked")
                }
            }
        }
    }

    if(helloDoorsToOpen!=null) {
        helloDoorsToOpen.open()
    }

    // check the thermostat
    if(thermostat!=null) {
        def tmode=thermostat.currentValue('thermostatMode')
        log.debug("thermostat: mode $tmode")
        if(tmode!=null) {
            if(tmode == "heat" && helloHeatTargetTemp!=null) {
                log.debug("set thermostat heat target temp to $helloHeatTargetTemp")
                thermostat.setHeatingSetpoint(helloHeatTargetTemp)
            } else if(tmode == "cool" && helloCoolTargetTemp!=null) {
                log.debug("set thermostat cool target temp to $helloCoolTargetTemp")
                thermostat.setCoolingSetpoint(helloCoolTargetTemp)
            } 
        }
    }

    log.debug("errors: $errMsg")
    if(errMsg!="") {
        sendNotificationEvent("HelloGoodbye: encountered errors: " + errMsg + "not all actions may have been successful")
    } else {
        sendNotificationEvent("HelloGoodbye: all actions completed")
    }
}

def doGoodbye() {
    def errMsg=""

    sendNotificationEvent("HelloGoodbye: Goodbye")

    // turn off the unconditional switches
    goodbyeSwitches?.off()

    if(goodbyeIfLowPowerSwitches!=null) {
        def lpt=goodbyeLowPowerThreshold ? goodbyeLowPowerThreshold : 10

        goodbyeIfLowPowerSwitches.each { s->
            if(s.currentValue('switch')=='on') {
                def power=s.currentValue('power')

                if(power < lpt) {
                    log.debug("turn off $s since $power < $lpt")
                    s.off()
                } else {
                    log.debug("can't turn off $s since $power >= $lpt")
                    errMsg+="can't turn off $s, "
                }
            } else {
                log.debug("switch $s is already off")
            }
        }
    }

    log.debug("number doors to lock: $goodbyenDoorLocks")
    if(goodbyenDoorLocks!=null && goodbyenDoorLocks>0) {
        (1..goodbyenDoorLocks).each { n->
            def door=settings."goodbyeDoorLock${n}"
            def sensor=settings."goodbyeDoorSensor${n}"
            log.debug("consider door: $door $sensor")

            if(door!=null) {
                if(door.currentValue('lock')=='unlocked') {
                    if(sensor==null  || (sensor.currentValue('contact')=='closed')) {
                        log.debug("locking $door")
                        door.lock()
                    } else {
                        errMsg+="can't lock door $door, $sensor is open, "
                    }
                } else {
                    log.debug("door $door already locked")
                }
            }
        }
    }

    if(goodbyeDoorsToClose!=null) {
        goodbyeDoorsToClose.close()
    }

    // check the thermostat
    if(thermostat!=null) {
        def tmode=thermostat.currentValue('thermostatMode')
        log.debug("thermostat: mode $tmode")
        if(tmode!=null) {
            if(tmode == "heat" && goodbyeHeatTargetTemp!=null) {
                log.debug("set thermostat heat target temp to $goodbyeHeatTargetTemp")
                thermostat.setHeatingSetpoint(goodbyeHeatTargetTemp)
            } else if(tmode == "cool" && goodbyeCoolTargetTemp!=null) {
                log.debug("set thermostat cool target temp to $goodbyeCoolTargetTemp")
                thermostat.setCoolingSetpoint(goodbyeCoolTargetTemp)
            } 
        }
    }


    log.debug("errors: $errMsg")
    if(errMsg!="") {
        sendNotificationEvent("HelloGoodbye: encountered errors: " + errMsg + "not all actions may have been successful")
    } else {
        sendNotificationEvent("HelloGoodbye: all actions completed")
    }
}

def routineChanged(evt) {
    if(!isEnabledBool) {
        log.debug("disabled, ignore")
        return
    } 

    log.debug("look at routine event: $evt")

    if(evt.displayName == routineHeadingHome) {
        if(presence.currentValue('presence')=='present') {
            log.debug("already home, running doHello")
            state.currentState="idle"
            doHello()            
        } else {
            log.debug("changing state to heading home")
            sendNotificationEvent("HelloGoodbye: enter heading home state")
            state.currentState="headingHome"
        }
    } else if(evt.displayName == routineHello) {
        log.debug("running doHello")
        state.currentState="idle"
        doHello()
    } else if(evt.displayName == routineLeaving) {
        if(presence.currentValue('presence')=='present') {
            log.debug("changing state to leaving home")
            sendNotificationEvent("HelloGoodbye: enter leaving home state")
            state.currentState="leavingHome";
        } else {
            log.debug("already left, running doGoodbye")
            state.currentState="idle"
            doGoodbye()
        }
    } else if(evt.displayName == routineGoodbye) {
        log.debug("running doGoodbye")
        state.currentState="idle"
        doGoodbye()
    }
}

def presenceEvent(evt) {
    if(!isEnabledBool) {
        log.debug("app is disabled, ignore")
        return
    }
    settings.isEnabledBool=false

    if(evt.value == "present") {
        if(state.currentState == "headingHome") {
            // move back to idle
            state.currentState="idle"
            sendPush("Almost home, waking up the house")
            doHello()
        }
    } else if(evt.value == "not present") {
        if(state.currentState == "leavingHome") {
            // move back to idle
            state.currentState="idle"
            sendPush("Goodbye, shutting down the house")
            doGoodbye()
        }
    }
}

def astroCheck() {
  def sunriseTimeOffset=(sunriseOffsetValue==null) ? 0 : timeOffset(sunriseOffsetValue)
  def sunsetTimeOffset=(sunsetOffsetValue==null) ? 0 : timeOffset(sunsetOffsetValue)
  def s = getSunriseAndSunset(zipCode: zipCode, sunriseOffset: sunriseTimeOffset, sunsetOffset: sunsetTimeOffset)
  state.riseTime = s.sunrise.time
  state.setTime = s.sunset.time
  log.debug "rise: ${new Date(state.riseTime)}($state.riseTime), set: ${new Date(state.setTime)}($state.setTime)"
}

def dark() {
    // check the sunrise/sunset times
    astroCheck()
  
    def t = now()
    def e = (t < state.riseTime) || (t > state.setTime)
    log.debug "dark check: $t $state $e"
    return (t < state.riseTime) || (t > state.setTime)
}
