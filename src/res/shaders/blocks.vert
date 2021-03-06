#version 440

layout(location = 0) in vec3 position;
layout(location = 1) in vec3 normal;
layout(location = 2) in vec2 tex;

layout(location = 3) in vec3 cubePos; // XYZ: index
layout(location = 4) in uint cubeType;

out vec3 cameraSpacePosition;
out vec3 norm;
out vec2 texCoord;

uniform mat4 projectionMatrix, viewMatrix;
uniform mat3 normalMatrix;

uniform float cubeSize;

uniform vec4 clipPlane;

void main() {
	vec4 worldPosition = vec4(cubeSize * (vec3(cubePos.xy, -cubePos.z) + position), 1);
	
	vec4 cameraPos = viewMatrix * worldPosition;
	
	cameraSpacePosition = vec3(cameraPos);
	gl_Position = projectionMatrix * cameraPos;
	
	norm = normalMatrix * normal;
	
	texCoord = tex;
	
	gl_ClipDistance[0] = dot(clipPlane.xyz, worldPosition.xyz) + clipPlane.w;
}
