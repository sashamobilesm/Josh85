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
    author: "Joshua Spain",
    description: "Virtual device handler for Lyric Leak sensor",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    singleInstance: true
    )

mappings {
    path("/oauth/initialize") {action: [GET: "oauthInitUrl"]}
    path("/oauth/callback") {action: [GET: "callback"]}
}

preferences {
	page(name: "auth", title: "Honeywell", content:"authPage", install:false)
    page(name: "selectDevices", title: "Device Selection", nextPage:"", content:"selectDevicesPage", install:true)
    }

//Prod
private static String LyricAPIEndpoint() { return "https://api.honeywell.com" }
private static String LyricAPIKey() {return "G4xxucb3RK4QbvcJdFIChsLNI8zhgDEK"}
private static String LyricAPISecret() {return "PCfhfrVf2V8gLBNZ"}
//Dev
//def LyricAPIEndpoint = ""

def getChildName() { return "Lyric Leak Sensor" }

// TODO: revokeAccessToken() on uninstall

def installed() {
    log.debug "Installed with settings: ${settings}"

    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"

    unsubscribe()
    initialize()
}

def uninstalled() {
    def delete = getAllChildDevices()
    delete.each { deleteChildDevice(it.deviceNetworkId) }
}

def initialize() {
	log.debug "initialize"
	def devs = settings.devices.collect { dni ->
    	//log.debug "processing ${dni}"
		def d = getChildDevice(dni)
		if(!d) {
			d = addChildDevice(app.namespace, getChildName(), dni, null, ["label":"${state.devices[dni]} Lyric" ?: "Lyric Leak Sensor"])
			log.debug "created ${d.displayName} with id $dni"
		} else {
			log.debug "found ${d.displayName} with id $dni already exists"
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
}

def authPage() {
    if(!state.accessToken) {
        // the createAccessToken() method will store the access token in state.accessToken
        createAccessToken()
    }
    
    def description
    def uninstallAllowed = false
    def oauthTokenProvided = false

    if(state.authToken) {
        description = "You are connected."
        uninstallAllowed = true
        oauthTokenProvided = true
    } else {
        description = "Click to enter Honeywell Credentials"
    }

    def redirectUrl = "https://graph.api.smartthings.com/oauth/initialize?appId=${app.id}&access_token=${state.accessToken}&apiServerUrl=${getApiServerUrl()}"
    // Check to see if SmartThings already has an access token from the third-party service.
    if(!oauthTokenProvided) {
    	//log.debug "Redirect URL (authPage) ${redirectUrl}"
        if (!oauthTokenProvided) {
            return dynamicPage(name: "auth", title: "Login", nextPage: "", uninstall:uninstallAllowed) {
                section("") {
                    paragraph "Tap below to log in to the Honeywell service and authorize SmartThings access. Be sure to scroll down on page 2 and press the 'Allow' button."
                    href url:redirectUrl, style:"embedded", required:true, title:"Honeywell", description:description
                }
            }
        }
    } else {
        refreshAuthToken()
        def locations = getLocations()
        log.debug "available location list: $locations"
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
    state.oauthInitState = UUID.randomUUID().toString()

    def oauthParams = [
        response_type: "code",
        scope: "smartRead,smartWrite",
        client_id: LyricAPIKey(),
        state: state.oauthInitState,
        redirect_uri: "https://graph.api.smartthings.com/oauth/callback"
    ]
	//log.debug "Redirecting to ${LyricAPIEndpoint()}/oauth2/authorize?${toQueryString(oauthParams)}"
    redirect(location: "${LyricAPIEndpoint()}/oauth2/authorize?${toQueryString(oauthParams)}")
}

String toQueryString(Map m) {
        return m.collect { k, v -> "${k}=${URLEncoder.encode(v.toString())}" }.sort().join("&")
}

def callback() {
    log.debug "callback()>> params: $params, params.code ${params.code}"

    def code = params.code
    def oauthState = params.state

    // Validate the response from the third party by making sure oauthState == state.oauthInitState as expected
    if (oauthState == state.oauthInitState){
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
                log.debug "response data: ${resp.data}"
                state.tokenExpiresIn = resp.data.expires_in
                state.refreshToken = resp.data.refresh_token
                state.authToken = resp.data.access_token
            }
        } 
        catch (e) {
            log.debug "something went wrong: $e"
        }

        if (state.authToken) {
            // call some method that will render the successfully connected message
            success()
        } else {
            // gracefully handle failures
            fail()
        }

    } else {
        log.error "callback() failed. Validation of state did not match. oauthState != state.oauthInitState"
    }
}

private getLocations() {
        def Params = [
        	uri: LyricAPIEndpoint(),
            path: "/v2/locations",
        	headers: ['Authorization': "Bearer ${state.authToken}"],
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
                    state.locations = []
                    resp.data.each { loc ->
                    try{
                        //def dni = [app.id, loc.locationID].join('.')
                        //log.debug "Found Location ID: ${loc.locationID} Name: ${loc.name}"
                        locs[loc.locationID] = loc.name
                        state.locations = state.locations == null ? loc : state.locations <<  locs
                        //log.debug "State.Locations = ${state.locations}"
                        }
                     catch (e) {
                        log.debug "Error $e"
                     }
				}
                } 
                else
                {
                    if (resp.status == 401) 
                    {
                        refreshAuthToken()
                    }
                }
            }
          }
        catch (e) {
            log.debug "something went wrong: $e"
            refreshAuthToken()
        }
        return locs
}

def selectDevicesPage(){
    def devs = [:]
    settings.Locations.each { loc ->
     log.debug "Getting devices for $loc"
     devs += getDevices(loc)
     state.devices = devs
    }
        log.debug "available devices list: ${state.devices}"
        return dynamicPage(name: "selectDevices", title: "Select Your Devices", nextPage: "", uninstall:false, install:true) {
            section("") {
                paragraph "Tap below to see the list of devices available in your Honeywell account and select the ones you want to connect to SmartThings."
                input(name: "devices", title:"Select Your Device(s)", type: "enum", required:false, multiple:true, description: "Tap to choose", metadata:[values:devs])
            }
        }
}

private getDevices(locID) {
        def Params = [
        	uri: LyricAPIEndpoint(),
            path: "/v2/devices",
        	headers: ['Authorization': "Bearer ${state.authToken}"],
            query: [
                apikey: LyricAPIKey(),
                locationId: locID
            ],
        ]
        
        def devs = [:]
        try {
            httpGet(Params) { resp ->
            	log.debug "getDevices httpGet response = ${resp.data}"
                if(resp.status == 200)
                {	
                    resp.data.each { dev ->
                    try{
                        def dni = [app.id, dev.deviceID].join('.')
                        //def dni = dev.deviceID
                        log.debug "Found device ID: ${dni} Name: ${dev.userDefinedDeviceName}"
                        devs[dni] = dev.userDefinedDeviceName
                        }
                     catch (e) {
                        log.debug "Error $e"
                     }
				}
                } 
                else
                {
                    if (resp.status == 401) 
                    {
                        refreshAuthToken()
                    }
                }
            }
          }
        catch (e) {
            log.debug "something went wrong: $e"
            refreshAuthToken()
        }
        return devs
}

private String getBase64AuthString() {
    String authorize = "${LyricAPIKey()}:${LyricAPISecret()}"
    String authorize_encoded = authorize.bytes.encodeBase64()
    return authorize_encoded
}

private refreshAuthToken() {
		log.debug "Refreshing your auth_token!"
        def Params = [
            uri: LyricAPIEndpoint(),
            path: "/oauth2/token",
            headers: ['Authorization': "Basic ${getBase64AuthString()}"],
            body: [
                grant_type: 'refresh_token',
                refresh_token: state.refreshToken
            ],
        ]
        
        try {
            httpPost(Params) { resp ->
            	log.debug resp.data
                if(resp.status == 200)
                {
                    if (resp.data) {
                        state.refreshToken = resp?.data?.refresh_token
                        state.authToken = resp?.data?.access_token
                    }
                }}
        }
        catch (e) {
            log.debug "something went wrong: $e"
        }

}

def executePost(Params) {
	try {
            httpPost(Params) { resp ->
                if(resp.status == 200)
                {
                    return resp
                } 
                else
                {
                    if (resp.status == 401 && resp.data.status.code == 14) 
                    {
                        log.debug "Refreshing your auth_token!"
                        refreshAuthToken()
                        executePost(Params)
                    }
                }
            }
          }
        catch (e) {
            log.debug "something went wrong: $e"
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