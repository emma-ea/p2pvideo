'use strict';

const PeerVideo = (() => {

    // ── Config ────────────────────────────────────────────────────────────
    const ICE_SERVERS = {
        iceServers: [
            { urls: 'stun:stun.l.google.com:19302' },
            // Add TURN credentials here for production (see spec §9.1)
        ]
    };

    // ── State ─────────────────────────────────────────────────────────────
    let ws;
    let pc;                    // RTCPeerConnection
    let localStream;
    let currentUsername  = null;
    let currentCallee    = null;
    let currentCaller    = null;
    let pendingOffer     = null; // stored offer SDP while user decides
    let appContextPath   = '';

    // ── Media control state ───────────────────────────────────────────────
    let audioMuted      = false;
    let videoOff        = false;
    let currentCameraId = null;
    let currentMicId    = null;

    // ── DOM helpers ───────────────────────────────────────────────────────
    const localVideo  = () => document.getElementById('localVideo');
    const remoteVideo = () => document.getElementById('remoteVideo');
    const videoArea   = () => document.getElementById('videoArea');
    const callerLabel = () => document.getElementById('callerLabel');

    // ── WebSocket connection ──────────────────────────────────────────────
    function connect(username, contextPath) {
        currentUsername = username;
        appContextPath  = contextPath || '';

        const protocol = location.protocol === 'https:' ? 'wss' : 'ws';
        ws = new WebSocket(`${protocol}://${location.host}${appContextPath}/signal`);

        ws.onopen = () => send({ type: 'register', from: currentUsername });

        ws.onmessage = (event) => {
            const msg = JSON.parse(event.data);
            switch (msg.type) {
                case 'presence':      handlePresence(msg);       break;
                case 'call-offer':    handleIncomingOffer(msg);  break;
                case 'call-answer':   handleAnswer(msg);         break;
                case 'ice-candidate': handleIceCandidate(msg);   break;
                case 'call-reject':   handleReject(msg);         break;
                case 'call-hangup':   handleHangup(msg);         break;
                case 'call-busy':     handleBusy(msg);           break;
            }
        };

        ws.onclose = () => {
            setTimeout(() => connect(currentUsername, appContextPath), 3000);
        };

        ws.onerror = (err) => console.error('WebSocket error:', err);
    }

    function send(msg) {
        if (ws && ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify(msg));
        }
    }

    // ── Media constraints (respects device selection) ─────────────────────
    function buildMediaConstraints() {
        const videoConstraint = currentCameraId
            ? { deviceId: { exact: currentCameraId } }
            : true;
        const audioConstraint = currentMicId
            ? { deviceId: { exact: currentMicId } }
            : true;
        return { video: videoConstraint, audio: audioConstraint };
    }

    // ── Outgoing call ─────────────────────────────────────────────────────
    async function call(calleeUsername) {
        if (pc) {
            alert('Already in a call.');
            return false;
        }
        currentCallee = calleeUsername;

        try {
            localStream = await navigator.mediaDevices.getUserMedia(buildMediaConstraints());
            localVideo().srcObject = localStream;

            pc = createPeerConnection(calleeUsername);
            localStream.getTracks().forEach(track => pc.addTrack(track, localStream));

            const offer = await pc.createOffer();
            await pc.setLocalDescription(offer);

            send({ type: 'call-offer', from: currentUsername, to: calleeUsername, sdp: offer.sdp });
            showVideoArea();
        } catch (err) {
            console.error('Error starting call:', err);
            alert('Could not access camera/microphone: ' + err.message);
            cleanupCall();
        }
        return false;
    }

    // ── Incoming call ─────────────────────────────────────────────────────
    function handleIncomingOffer(msg) {
        if (pc) {
            send({ type: 'call-reject', from: currentUsername, to: msg.from });
            return;
        }
        currentCaller = msg.from;
        pendingOffer  = msg.sdp;
        if (callerLabel()) callerLabel().textContent = `${msg.from} is calling you…`;
        if (typeof PF !== 'undefined') PF('incomingCallDialog').show();
    }

    async function acceptCall() {
        if (typeof PF !== 'undefined') PF('incomingCallDialog').hide();
        try {
            localStream = await navigator.mediaDevices.getUserMedia(buildMediaConstraints());
            localVideo().srcObject = localStream;

            pc = createPeerConnection(currentCaller);
            localStream.getTracks().forEach(track => pc.addTrack(track, localStream));

            await pc.setRemoteDescription({ type: 'offer', sdp: pendingOffer });
            const answer = await pc.createAnswer();
            await pc.setLocalDescription(answer);

            send({ type: 'call-answer', from: currentUsername, to: currentCaller, sdp: answer.sdp });
            showVideoArea();
        } catch (err) {
            console.error('Error accepting call:', err);
            alert('Could not access camera/microphone: ' + err.message);
            rejectCall();
        }
    }

    function rejectCall() {
        if (typeof PF !== 'undefined') PF('incomingCallDialog').hide();
        send({ type: 'call-reject', from: currentUsername, to: currentCaller });
        currentCaller = null;
        pendingOffer  = null;
    }

    // ── RTCPeerConnection factory ─────────────────────────────────────────
    function createPeerConnection(remoteUsername) {
        const connection = new RTCPeerConnection(ICE_SERVERS);

        connection.onicecandidate = ({ candidate }) => {
            if (candidate) {
                send({ type: 'ice-candidate', from: currentUsername, to: remoteUsername,
                       candidate: candidate.toJSON() });
            }
        };

        connection.ontrack = ({ streams }) => {
            if (remoteVideo()) remoteVideo().srcObject = streams[0];
        };

        connection.onconnectionstatechange = () => {
            if (['disconnected', 'failed', 'closed'].includes(connection.connectionState)) {
                cleanupCall();
            }
        };

        return connection;
    }

    // ── Answer / ICE handling ─────────────────────────────────────────────
    async function handleAnswer(msg) {
        if (pc) {
            await pc.setRemoteDescription({ type: 'answer', sdp: msg.sdp });
        }
    }

    async function handleIceCandidate(msg) {
        if (pc && msg.candidate) {
            try {
                await pc.addIceCandidate(new RTCIceCandidate(msg.candidate));
            } catch (e) {
                console.warn('ICE candidate error:', e);
            }
        }
    }

    // ── Presence ──────────────────────────────────────────────────────────
    function handlePresence(msg) {
        if (typeof refreshUserList !== 'undefined') {
            refreshUserList();
        }
    }

    // ── Call end / rejection ──────────────────────────────────────────────
    function handleReject() {
        alert('Call was rejected.');
        cleanupCall();
    }

    function handleHangup() {
        cleanupCall();
    }

    function handleBusy() {
        alert('User is currently busy.');
        cleanupCall();
    }

    function hangup() {
        const target = currentCallee || currentCaller;
        if (target) send({ type: 'call-hangup', from: currentUsername, to: target });
        cleanupCall();
    }

    // ── Cleanup ───────────────────────────────────────────────────────────
    function cleanupCall() {
        if (pc) { pc.close(); pc = null; }
        if (localStream) {
            localStream.getTracks().forEach(t => t.stop());
            localStream = null;
        }
        if (localVideo())  localVideo().srcObject  = null;
        if (remoteVideo()) remoteVideo().srcObject = null;
        if (videoArea())   videoArea().style.display = 'none';
        currentCallee = null;
        currentCaller = null;
        pendingOffer  = null;

        // Reset media control state
        audioMuted = false;
        videoOff   = false;

        const muteBtn   = document.getElementById('muteBtn');
        const cameraBtn = document.getElementById('cameraBtn');
        const offBadge  = document.getElementById('cameraOffBadge');
        const localVid  = localVideo();

        if (muteBtn)   muteBtn.querySelector('.ui-icon').className   = 'ui-icon pi pi-microphone';
        if (cameraBtn) {
            cameraBtn.querySelector('.ui-icon').className = 'ui-icon pi pi-video';
            cameraBtn.classList.remove('ui-button-warning');
            cameraBtn.classList.add('ui-button-secondary');
        }
        if (localVid)  localVid.style.display  = 'block';
        if (offBadge)  offBadge.style.display  = 'none';
    }

    function showVideoArea() {
        if (videoArea()) videoArea().style.display = 'block';
    }

    // ── Device enumeration ────────────────────────────────────────────────
    async function enumerateDevices() {
        let devices;
        try {
            devices = await navigator.mediaDevices.enumerateDevices();
        } catch (e) {
            console.warn('enumerateDevices failed:', e);
            return;
        }

        const cameras = devices.filter(d => d.kind === 'videoinput');
        const mics    = devices.filter(d => d.kind === 'audioinput');

        populateSelect('cameraSelect',       cameras, currentCameraId);
        populateSelect('micSelect',          mics,    currentMicId);
        populateSelect('inCallCameraSelect', cameras, currentCameraId);
        populateSelect('inCallMicSelect',    mics,    currentMicId);
    }

    function populateSelect(selectId, devices, activeDeviceId) {
        const el = document.getElementById(selectId);
        if (!el) return;

        const prev = el.value;
        el.innerHTML = '';

        devices.forEach((device, i) => {
            const opt = document.createElement('option');
            opt.value = device.deviceId;
            opt.text  = device.label || `Device ${i + 1}`;
            if (device.deviceId === (activeDeviceId || prev)) opt.selected = true;
            el.appendChild(opt);
        });
    }

    // Re-enumerate when a device is plugged in or removed
    navigator.mediaDevices.addEventListener('devicechange', enumerateDevices);

    // ── Toggle mute ───────────────────────────────────────────────────────
    function toggleMute() {
        if (!localStream) return;

        audioMuted = !audioMuted;
        localStream.getAudioTracks().forEach(track => { track.enabled = !audioMuted; });

        const btn = document.getElementById('muteBtn');
        if (btn) {
            btn.querySelector('.ui-icon').className =
                audioMuted ? 'ui-icon pi pi-microphone-slash'
                           : 'ui-icon pi pi-microphone';
            btn.title = audioMuted ? 'Unmute microphone' : 'Mute microphone';
        }
    }

    // ── Toggle camera ─────────────────────────────────────────────────────
    function toggleCamera() {
        if (!localStream) return;

        videoOff = !videoOff;
        localStream.getVideoTracks().forEach(track => { track.enabled = !videoOff; });

        const localVid  = document.getElementById('localVideo');
        const offBadge  = document.getElementById('cameraOffBadge');
        if (localVid) localVid.style.display = videoOff ? 'none' : 'block';
        if (offBadge) offBadge.style.display  = videoOff ? 'flex'  : 'none';

        const btn = document.getElementById('cameraBtn');
        if (btn) {
            btn.querySelector('.ui-icon').className =
                videoOff ? 'ui-icon pi pi-video-slash'
                         : 'ui-icon pi pi-video';
            btn.title = videoOff ? 'Turn on camera' : 'Turn off camera';
            btn.classList.toggle('ui-button-warning',   videoOff);
            btn.classList.toggle('ui-button-secondary', !videoOff);
        }
    }

    // ── Switch camera mid-call ────────────────────────────────────────────
    async function switchCamera(deviceId) {
        if (!deviceId) return;
        currentCameraId = deviceId;

        if (!pc || !localStream) return;

        let newStream;
        try {
            newStream = await navigator.mediaDevices.getUserMedia({
                video: { deviceId: { exact: deviceId } },
                audio: false
            });
        } catch (e) {
            console.error('Could not switch camera:', e);
            return;
        }

        const newVideoTrack = newStream.getVideoTracks()[0];

        const sender = pc.getSenders().find(s => s.track && s.track.kind === 'video');
        if (sender) await sender.replaceTrack(newVideoTrack);

        localStream.getVideoTracks().forEach(t => t.stop());
        localStream.removeTrack(localStream.getVideoTracks()[0]);
        localStream.addTrack(newVideoTrack);

        if (localVideo()) localVideo().srcObject = localStream;
        newVideoTrack.enabled = !videoOff;

        const cameras = await getCameraList();
        populateSelect('cameraSelect',       cameras, currentCameraId);
        populateSelect('inCallCameraSelect', cameras, currentCameraId);
    }

    // ── Switch microphone mid-call ────────────────────────────────────────
    async function switchMicrophone(deviceId) {
        if (!deviceId) return;
        currentMicId = deviceId;

        if (!pc || !localStream) return;

        let newStream;
        try {
            newStream = await navigator.mediaDevices.getUserMedia({
                audio: { deviceId: { exact: deviceId } },
                video: false
            });
        } catch (e) {
            console.error('Could not switch microphone:', e);
            return;
        }

        const newAudioTrack = newStream.getAudioTracks()[0];

        const sender = pc.getSenders().find(s => s.track && s.track.kind === 'audio');
        if (sender) await sender.replaceTrack(newAudioTrack);

        localStream.getAudioTracks().forEach(t => t.stop());
        localStream.removeTrack(localStream.getAudioTracks()[0]);
        localStream.addTrack(newAudioTrack);

        newAudioTrack.enabled = !audioMuted;

        const mics = await getMicList();
        populateSelect('micSelect',       mics, currentMicId);
        populateSelect('inCallMicSelect', mics, currentMicId);
    }

    // ── Device list helpers ───────────────────────────────────────────────
    async function getCameraList() {
        const devices = await navigator.mediaDevices.enumerateDevices();
        return devices.filter(d => d.kind === 'videoinput');
    }

    async function getMicList() {
        const devices = await navigator.mediaDevices.enumerateDevices();
        return devices.filter(d => d.kind === 'audioinput');
    }

    // ── Public API ────────────────────────────────────────────────────────
    return {
        connect, call, acceptCall, rejectCall, hangup,
        toggleMute, toggleCamera,
        switchCamera, switchMicrophone,
        enumerateDevices
    };

})();
