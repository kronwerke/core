#version 150

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 dir;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // on the far plane: drawn after the world, the depth test lets it through only where there is sky
    gl_Position.z = gl_Position.w * 0.999999;
    dir = Position;
}
