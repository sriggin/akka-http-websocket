var socket = null;
var username = document.getElementById("username");

var message = document.getElementById("message");

var left = document.getElementById("left");
var right = document.getElementById("right");

function append(elem, text) {
  elem.innerHTML += "<br/>" +  text; // TODO
}

//TODO support wss:// somehow
console.log(window.location);



function join() {
  var value = username.value;

  if(value && value !== "Username") {
    var wsProtocol;
    switch(window.location.protocol) {
    case 'http:': wsProtocol = 'ws:'; break;
    case 'https:': wsProtocol = 'wss:'; break;
    default: throw new Error("Unsupported protocol used in page: " + window.location.protocol);
    }
    socket = new WebSocket(wsProtocol + '//' + window.location.host + '/join?name=' + value);
    socket.onmessage = function(event) {
      var message = event.data;
      console.log('Received:', event);
      append(left, message);
    };
  }
}

function send() {
  var value = message.value;

  if(value) {
    socket.send(value);
  }
}
