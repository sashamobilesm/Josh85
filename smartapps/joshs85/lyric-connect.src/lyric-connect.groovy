/**
 *  Lyric
 *
 *  Copyright 2017 Joshua Spain
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
    name: "Lyric (Connect)",
    namespace: "joshs85",
    author: "Joshua S",
    description: "Virtual device handler for Lyric Leak sensor",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    singleInstance: false
    )
{
    appSetting "LyricAPI_Key"
    appSetting "LyricAPI_Secret"
}

mappings {
    path("/oauth/initialize") {action: [GET: "oauthInitUrl"]}
    path("/oauth/callback") {action: [GET: "callback"]}
}

preferences {
	page(name: "auth", title: "Honeywell", content:"authPage", install:false)
    page(name: "selectDevices", title: "Device Selection", content:"selectDevicesPage", install:false)
    page(name: "settings", title: "Settings", content: "settingsPage", install:true)
    }

private static String LyricAPIEndpoint() { return "https://api.honeywell.com" }
private String LyricAPIKey() {return appSettings.LyricAPI_Key}
private String LyricAPISecret() {return appSettings.LyricAPI_Secret}
def getChildName() { return "Lyric Leak Sensor" }

def installed() {
    log.info "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    log.info "Updated with settings: ${settings}"

    unsubscribe()
    initialize()
}

def uninstalled() {
    def delete = getAllChildDevices()
    delete.each { deleteChildDevice(it.deviceNetworkId) }
}

def initialize() {
	log.debug "Entering the initialize method"
	def devs = settings.devices.collect { dni ->
    	//log.debug "processing ${dni}"
		def d = getChildDevice(dni)
		if(!d) {
        	def devlist = atomicState.devices
			d = addChildDevice(app.namespace, getChildName(), dni, null, ["label":"${devlist[dni]} Lyric" ?: "Lyric Leak Sensor"])
			log.info "created ${d.displayName} with id $dni"
		} else {
			log.info "found ${d.displayName} with id $dni already exists"
		}
		return d
	}
    
	//log.debug "created ${devs.size()} leak sensors."

	def delete  // Delete any that are no longer in settings
	if(!devs) {
		//log.debug "delete all leak sensors"
		delete = getAllChildDevices() //inherits from SmartApp (data-management)
	} else {
		//log.debug "delete individual thermostat and sensor"
		delete = getChildDevices().findAll { !settings.devices.contains(it.deviceNetworkId)}
	}
	log.warn "delete: ${delete}, deleting ${delete.size()} leak sensors"
	delete.each { deleteChildDevice(it.deviceNetworkId) } //inherits from SmartApp (data-management)
	
    try{
    	pollChildren()
    }
    catch (e)
    {
    	log.debug "Error with initial polling: $e"
    }
    
	runEvery1Minute("pollChildren")
}

def authPage() {
    if(!atomicState.accessToken) {
        // the createAccessToken() method will store the access token in atomicState.accessToken
        createAccessToken()
        atomicState.accessToken = state.accessToken
    }
    
    def description
    def uninstallAllowed = false
    def oauthTokenProvided = false

    if(atomicState.authToken) {
        description = "You are connected."
        uninstallAllowed = true
        oauthTokenProvided = true
    } else {
        description = "Click to enter Honeywell Credentials"
    }

    def redirectUrl = "https://graph.api.smartthings.com/oauth/initialize?appId=${app.id}&access_token=${atomicState.accessToken}&apiServerUrl=${getApiServerUrl()}"
    // Check to see if SmartThings already has an access token from the third-party service.
    if(!oauthTokenProvided) {
    	log.debug "Redirect URL (authPage) ${redirectUrl}"
        if (!oauthTokenProvided) {
            return dynamicPage(name: "auth", title: "Login", nextPage: "", uninstall:uninstallAllowed) {
                section("") {
                    paragraph "Tap below to log in to the Honeywell service and authorize SmartThings access. Be sure to scroll down on page 2 and press the 'Allow' button."
                    href url:redirectUrl, style:"embedded", required:true, title:"Honeywell", description:description
                }
            }
        }
    } else {
        def locations = getLocations()
        log.info "available location list: $locations"
        return dynamicPage(name: "auth", title: "Select Your Location", nextPage: "selectDevices", uninstall:uninstallAllowed) {
            section("") {
                paragraph "Tap below to see the list of locations available in your Honeywell account and select the ones you want to connect to SmartThings."
                input(name: "Locations", title:"Select Your Location(s)", type: "enum", required:true, multiple:true, description: "Tap to choose", metadata:[values:locations])
            }
        }
    }
}

def oauthInitUrl() {

    // Generate a random ID to use as a our state value. This value will be used to verify the response we get back from the third-party service.
    atomicState.oauthInitState = UUID.randomUUID().toString()

    def oauthParams = [
        response_type: "code",
        scope: "smartRead,smartWrite",
        client_id: LyricAPIKey(),
        "state": atomicState.oauthInitState,
        redirect_uri: "https://graph.api.smartthings.com/oauth/callback"
    ]
	log.debug "Redirecting to ${LyricAPIEndpoint()}/oauth2/authorize?${toQueryString(oauthParams)}"
    redirect(location: "${LyricAPIEndpoint()}/oauth2/authorize?${toQueryString(oauthParams)}")
}

String toQueryString(Map m) {
        return m.collect { k, v -> "${k}=${URLEncoder.encode(v.toString())}" }.sort().join("&")
}

def callback() {
    log.debug "callback()>> params: $params, params.code ${params.code}"

    def code = params.code
    def oauthState = params.state

    // Validate the response from the third party by making sure oauthState == atomicState.oauthInitState as expected
    if (oauthState == atomicState.oauthInitState){
        def Params = [
        	uri: LyricAPIEndpoint(),
            path: "/oauth2/token",
        	headers: ['Authorization': "Basic ${getBase64AuthString()}"],
            body: [
                grant_type: 'authorization_code',
                code: code,
                redirect_uri: "https://graph.api.smartthings.com/oauth/callback"
            ],
        ]
        
        try {
            httpPost(Params) { resp ->
                log.debug "refresh auth token response data: ${resp.data}"
                atomicState.tokenExpiresIn = resp.data.expires_in
                atomicState.refreshToken = resp.data.refresh_token
                atomicState.authToken = resp.data.access_token
            }
        } 
        catch (e) {
            log.error "Error in the callback mathod: $e"
        }

        if (atomicState.authToken) {
            // call some method that will render the successfully connected message
            success()
        } else {
            // gracefully handle failures
            fail()
        }

    } else {
        log.error "callback() failed. Validation of state did not match. oauthState != atomicState.oauthInitState"
    }
}

private getLocations() {
		log.debug "Entering the getLocations method"
        refreshAuthToken()
        def Params = [
        	uri: LyricAPIEndpoint(),
            path: "/v2/locations",
        	headers: ['Authorization': "Bearer ${atomicState.authToken}"],
            query: [
                apikey: LyricAPIKey()
            ],
        ]
        
        def locs = [:]
        try {
            httpGet(Params) { resp ->
            	//log.debug "getLocations httpGet response = ${resp.data}"
                if(resp.status == 200)
                {	
                    atomicState.locations = []
                    resp.data.each { loc ->
                    try{
                        //def dni = [app.id, loc.locationID].join('.')
                        //log.debug "Found Location ID: ${loc.locationID} Name: ${loc.name}"
                        locs[loc.locationID] = loc.name
                        atomicState.locations = atomicState.locations == null ? loc : atomicState.locations <<  locs
                        log.debug "atomicState.Locations = ${atomicState.locations}"
                        }
                     catch (e) {
                        log.error "Error in getLocations resp: $e"
                     }
				}
                } 
            }
          }
        catch (e) {
            log.error "Error in getLocations: $e"
        }
        return locs
}

def selectDevicesPage(){
	log.debug "Entering the selectDevicesPage method"
    def devs = [:]
    def devicelocations = [:]
    settings.Locations.each { loc ->
     log.debug "Getting devices for ${loc}"
     devs += getDevices(loc)
     atomicState.devices = devs
     devs.each { dev -> 
     	devicelocations[dev.key] = loc
        atomicState.devicelocations = devicelocations
     }
     log.debug "devicelocations = ${devicelocations}"
    }
        log.debug "available devices list: ${devs}"
        return dynamicPage(name: "selectDevices", title: "Select Your Devices", nextPage: "settings", uninstall:false, install:false) {
            section("") {
                paragraph "Tap below to see the list of devices available in your Honeywell account and select the ones you want to connect to SmartThings."
                input(name: "devices", title:"Select Your Device(s)", type: "enum", required:false, multiple:true, description: "Tap to choose", metadata:[values:devs])
            }
        }
}

private settingsPage(){
    return dynamicPage(name: "settings", title: "Settings", nextPage: "", uninstall:false, install:true) {
                section("") {
                    input "DisplayTempInF", "boolean", title: "Display temperatures in Fahrenheit?", defaultValue: true, required: false
                }
            }
}

private getDevices(locID) {
		log.debug "Enter getDevices"
		refreshAuthToken()
        def Params = [
        	uri: LyricAPIEndpoint(),
            path: "/v2/devices",
        	headers: ['Authorization': "Bearer ${atomicState.authToken}"],
            query: [
                apikey: LyricAPIKey(),
                locationId: locID
            ],
        ]
        
        def devs = [:]
        def deviceids = [:]
        try {
            httpGet(Params) { resp ->
            	log.debug "getDevices httpGet response = ${resp.data}"
                if(resp.status == 200)
                {	
                    resp.data.each { dev ->
                    try{
                        def dni = [app.id, dev.deviceID].join('.')
                        log.debug "Found device ID: ${dni} Name: ${dev.userDefinedDeviceName}"
                        devs[dni] = dev.userDefinedDeviceName
                        deviceids[dni] = dev.deviceID
                        }
                     catch (e) {
                        log.error "Error in getDevices: $e"
                     }
				}
                } 
            }
            atomicState.deviceids = deviceids
          }
        catch (e) {
            log.error "Error in getDevices: $e"
        }
        return devs
}

private String getBase64AuthString() {
    String authorize = "${LyricAPIKey()}:${LyricAPISecret()}"
    String authorize_encoded = authorize.bytes.encodeBase64()
    return authorize_encoded
}

private refreshAuthToken() {
		if (testAuthToken() == false) {
            log.info "Refreshing your auth_token!"
            def Params = [
                uri: LyricAPIEndpoint(),
                path: "/oauth2/token",
                headers: ['Authorization': "Basic ${getBase64AuthString()}"],
                body: [
                    grant_type: 'refresh_token',
                    refresh_token: atomicState.refreshToken
                ],
            ]

            try {
                httpPost(Params) { resp ->
                    log.debug resp.data
                    if(resp.status == 200)
                    {
                        if (resp.data) {
                            atomicState.refreshToken = resp?.data?.refresh_token
                            atomicState.authToken = resp?.data?.access_token
                            atomicState.tokenExpiresIn = resp?.data?.expires_in
                            log.info "Token refresh Success."
                        }
                    }}
            }
            catch (e) {
                log.error "Error in the refreshAuthToken method: $e"
            }

    }
}

private testAuthToken() {
        def Params = [
        	uri: LyricAPIEndpoint(),
            path: "/v2/locations",
        	headers: ['Authorization': "Bearer ${atomicState.authToken}"],
            query: [
                apikey: LyricAPIKey()
            ],
        ]
        
        try {
           httpGet(Params) { resp -> 
            //log.debug "testAuthToken response: ${resp}"
           	if(resp.status == 200) {
            	log.info "Auth code test success. Status: ${resp.status}"
            	return true
            }
            else {
            	log.warn "Status != 200 while testing current auth code. Response=${resp.data}, Status: ${resp.status}"
				return false
            }
           }
        }
            catch (e) {
            	log.error "Error while testing auth code: $e"
            	return false
        	}
}

def pollChildren(){
		log.info "starting pollChildren"
		refreshAuthToken()
		atomicState.devicedetails = [:]
        def deviceids = atomicState.deviceids
        def devicelocations = atomicState.devicelocations
		settings.devices.each {dev ->
            def deviceid = deviceids[dev]
            def locationid = devicelocations[dev]
            def d = getChildDevice(dev)
            def Params = [
                uri: LyricAPIEndpoint(),
                path: "/v2/devices/waterLeakDetectors/${deviceid}",
                headers: ['Authorization': "Bearer ${atomicState.authToken}"],
                query: [
                    apikey: LyricAPIKey(),
                    locationId: locationid
                ],
            ]
            
            log.debug "starting httpGet with Params = ${Params}"
            httpGet(Params) { resp ->
            try{
            	def devicedetails = atomicState.devicedetails
                devicedetails[dev] = resp.data
                atomicState.devicedetails = devicedetails
                
                def waterPresent = resp.data.waterPresent == true ? "wet" : "dry"
                def humidity = resp.data.currentSensorReadings.humidity
                def temp = resp.data.currentSensorReadings.temperature
                def battery = resp.data.batteryRemaining
                def offline = resp.data.isDeviceOffline
                def temphigh = resp.data.deviceSettings.temp.high.limit
                def templow = resp.data.deviceSettings.temp.low.limit
                def tempAlarm = "normal"
                def buzzerMuted = resp.data.deviceSettings.buzzerMuted
                if (temp <= temphigh && temp >= templow) {tempAlarm = "normal"}
                else if (temp > temphigh) {tempAlarm = "overheated"}
                else if (temp < templow) {tempAlarm = "freezing"}
                if (settings.DisplayTempInF) {temp = convertCtoF(temp)}
                def events = [
                	['water': waterPresent],
                    ['temperature': temp],
                    ['humidity': humidity],
                    ['battery': battery],
                    ['powerSource': 'battery'],
                    ['DeviceStatus': offline == true ? "offline" : "online"],
                    ['temperatureAlarm': tempAlarm],
                    ['buzzerMuted': buzzerMuted],
                	]
                log.info "Sending events: ${events}"
                events.each {event -> d.generateEvent(event)}
                log.debug "device data for ${deviceid} = ${devicedetails[dev]}"
                }
                catch (e)
                {
                	log.error "Error while processing events for pollChildren: ${e}"
				}
            }
    }
}

def success() {
        def message = """
                <p><h1>Your account is now connected to SmartThings!</h1></p>
                <p><h2>Click 'Done' to finish setup.</h2></p>
        """
        displayMessageAsHtml(message)
}

def fail() {
    def message = """
        <p>There was an error connecting your account with SmartThings</p>
        <p>Please try again.</p>
    """
    displayMessageAsHtml(message)
}

def displayMessageAsHtml(message) {
    def html = """
        <!DOCTYPE html>
        <html>
            <head>
            </head>
            <body>
                <div>
                    ${message}
                </div>
            </body>
        </html>
    """
    render contentType: 'text/html', data: html
}

def convertCtoF(tempC) {
	float tempF = Math.round((tempC * 1.8) + 32)
	return String.format("%.1f", tempF)
}