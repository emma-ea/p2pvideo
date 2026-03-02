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

    // ── DOM helpers ───────────────────────────────────────────────────────
    const localVideo  = () => document.getElementById('localVideo');
    const remoteVideo = () => document.getElementById('remoteVideo');
    const videoArea   = () => document.getElementById('videoArea');
    const callerLabel = () => document.getElementById('callerLabel');

    // ── WebSocket connection ──────────────────────────────────────────────
    function connect(username, contextPath) {
        currentUsername  = username;
        appContextPath   = contextPath || '';

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
            // Reconnect after 3 seconds if connection drops
            setTimeout(() => connect(currentUsername, appContextPath), 3000);
        };

        ws.onerror = (err) => console.error('WebSocket error:', err);
    }

    function send(msg) {
        if (ws && ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify(msg));
        }
    }

    // ── Outgoing call ─────────────────────────────────────────────────────
    async function call(calleeUsername) {
        if (pc) {
            alert('Already in a call.');
            return false;
        }
        currentCallee = calleeUsername;

        try {
            localStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
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
            // Already in a call — the server should have sent call-busy, but handle defensively
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
            localStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
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
        // Trigger PrimeFaces AJAX to refresh the server-rendered user list
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
    }

    function showVideoArea() {
        if (videoArea()) videoArea().style.display = 'block';
    }

    // ── Public API ────────────────────────────────────────────────────────
    return { connect, call, acceptCall, rejectCall, hangup };

})();
