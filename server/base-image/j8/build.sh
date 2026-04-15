# use server/base-image as build context so Dockerfile can COPY common/start-server.sh
docker build \
 --build-arg HTTP_PROXY="http://192.168.1.2:7890" \
 --build-arg HTTPS_PROXY="http://192.168.1.2:7890" \
 --build-arg NO_PROXY="127.0.0.1,localhost" \
 -f Dockerfile \
 -t rdi:j8 ..
