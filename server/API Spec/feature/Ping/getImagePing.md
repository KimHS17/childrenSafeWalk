# ID 기반 이미지 정보 가져오기

## API 명세서 
### 입력 값 :
```
GET으로 받음.
ex)
getImagePing.php?id=value
```
### 2. 출력 값 :
   1. 성공 한 경우
```
이미지 바이너리 데이터
```
   1. 실패 한 경우 해당 id와 파일이 없는 경우
```
빈 이미지의 바이너리 데이터
```