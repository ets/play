FROM java:7
COPY . /usr/src/play/
WORKDIR /usr/src/play

CMD ["play"]
