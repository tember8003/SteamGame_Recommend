package SteamGame.recommend.domain.game.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class GameTagId implements Serializable {
    private Long game;
    private Long tag;

    @Override
    public boolean equals(Object o){
        if(this == o){
            return true;
        }
        if(!(o instanceof GameTagId)){
            return false;
        }
        GameTagId gameTagId = (GameTagId) o;

        return Objects.equals(game,gameTagId.game) && Objects.equals(tag,gameTagId.tag);
    }

    @Override
    public int hashCode(){
        return Objects.hash(game,tag);
    }

}
