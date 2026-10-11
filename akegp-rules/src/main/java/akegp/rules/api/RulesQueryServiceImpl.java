package akegp.rules.api;
import akegp.shared.api.RuleQueryService;
import akegp.shared.dto.RuleDto;
import java.util.List;
import org.springframework.stereotype.Service;
@Service
public class RulesQueryServiceImpl implements RuleQueryService {
  public List<RuleDto> findAll() { return List.of(); }
}
