/**
 *
 * Heading Home 
 *
 * Author: Eric Chesters
 */


definition(
  	name: "GargeDoorGuard",
  	namespace: "sbairn",
	author: "sbairn",
	description: "Make sure the garage door is closed.",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/light_motion-outlet-luminance.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/light_motion-outlet-luminance@2x.png"
)



preferences {
    section("Enabled"){
        input(name: "isEnabledBool", type: "bool", title: "Enabled ?")
    } 
    section("Garage door") {
        input "door", "capability.doorControl", title: "Which garage door controller?"
    }
    section("Close after") {
        input "closeAfterMinutes", "number", title: "Wait N minutes before closing after the door is opened", required: true 
    }
    section("Time window") {
        input "timeStart", "time", title: "Start time"
        input "timeEnd", "time", title: "End time"
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
    log.debug("init")
    state.closeAfter=null
    state.currentState="idle"
    schedule("0 */2 * * * ?", doCheck)
    subscribe(door, "door", doorEvent)
   
    def doorState=door.currentValue("door")
    log.debug("door state $doorState")
    if(doorState == "open") {
        Date now=new Date()
        state.closeAfter=now.getTime()+(60*1000*closeAfterMinutes)
    }

    doCheck()
}

def doorEvent(evt) {
    log.debug("door event: $evt.value")

    if(evt.value == "closed") {
        state.closeAfter=null
        state.currentState="idle"
    } else if (evt.value == "open") {
        // someone opened the door, set teh closeAfter Date to now + 10 mins
        Date now=new Date()
        state.closeAfter=now.getTime()+(60*1000*closeAfterMinutes)
    }
}

def doCheck() {
    if(!isEnabledBool) {
        log.debug("disabled, skipping doCheck")
        return
    }

    log.debug("doCheck: $state")

    def now = new Date()
    def start = timeToday(timeStart, location.timeZone)
    def end = timeTodayAfter(timeStart,timeEnd, location.timeZone)

    if(now.after(start) && now.before(end)) {
        def doorState=door.currentValue("door")
        log.debug("in window: state $doorState")
        if(doorState == "open") {
            if(state.currentState=="idle") {
                if((state.closeAfter == null) || (now.getTime() > state.closeAfter)) {
                    sendPush("Garage door left open, attempting to close")
                    door.close()
                    state.closeAfter = null
                    state.currentState="closing"
                } else {
                    log.debug("door is open however we are waiting until we reach the close after time")
                }
            } else {
                sendPush("Failed to close garage door, please check and close the door")                
            }
        } else {
            log.debug("door is closed")
            state.currentState="idle"
        }
    } else {
        log.debug("out of window, don't check")
    }

}