## Screenshots

![Screenshot 1](path/to/screenshot1.png){: .responsive-img }
![Screenshot 2](path/to/screenshot2.png){: .responsive-img }
![Screenshot 3](path/to/screenshot3.png){: .responsive-img }
![Screenshot 4](path/to/screenshot4.png){: .responsive-img }

<div class="grid-container">
  <div class="grid-item">
    <img src="path/to/screenshot1.png" alt="Screenshot 1" class="responsive-img">
  </div>
  <div class="grid-item">
    <img src="path/to/screenshot2.png" alt="Screenshot 2" class="responsive-img">
  </div>
  <div class="grid-item">
    <img src="path/to/screenshot3.png" alt="Screenshot 3" class="responsive-img">
  </div>
  <div class="grid-item">
    <img src="path/to/screenshot4.png" alt="Screenshot 4" class="responsive-img">
  </div>
</div>

<style>
.grid-container {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.responsive-img {
  width: 100%;
  height: auto;
}
</style>
